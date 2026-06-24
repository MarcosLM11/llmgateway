package com.marcos.llmgateway.metering.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import com.marcos.llmgateway.AbstractIntegrationTest;
import com.marcos.llmgateway.metering.UsageEvent;
import com.marcos.llmgateway.metering.UsageEventPublisher;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

class CostTrackingIT extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UsageQueryService usageQueryService;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM usage_events");
    }

    @Test
    void cacheMiss_withPricingRule_persistsCalculatedCost() {
        // gpt-4o-mini: $0.15 prompt / $0.60 completion per 1M — seeded in V4 migration
        // 100 prompt + 50 completion = 0.000015 + 0.000030 = 0.000045
        var event = new UsageEvent(
                "req-cost-001", "tenant-cost", "gpt-4o-mini", "openai",
                100, 50, false, 200, Instant.now()
        );

        kafkaTemplate.send(UsageEventPublisher.USAGE_EVENTS_TOPIC, event.tenantId(), event);

        await().atMost(30, SECONDS).untilAsserted(() -> {
            BigDecimal cost = jdbcTemplate.queryForObject(
                    "SELECT estimated_cost_usd FROM usage_events WHERE request_id = ?",
                    BigDecimal.class,
                    "req-cost-001"
            );
            assertThat(cost).isNotNull();
            // prompt: 100/1_000_000 * 0.15 = 0.000015 → 0.00001500
            // completion: 50/1_000_000 * 0.60 = 0.000030 → 0.00003000
            assertThat(cost).isEqualByComparingTo(new BigDecimal("0.00004500"));
        });
    }

    @Test
    void cacheHit_persistsZeroCost() {
        var event = new UsageEvent(
                "req-cost-002", "tenant-cost", "gpt-4o-mini", "openai",
                100, 50, true, 5, Instant.now()
        );

        kafkaTemplate.send(UsageEventPublisher.USAGE_EVENTS_TOPIC, event.tenantId(), event);

        await().atMost(30, SECONDS).untilAsserted(() -> {
            BigDecimal cost = jdbcTemplate.queryForObject(
                    "SELECT estimated_cost_usd FROM usage_events WHERE request_id = ?",
                    BigDecimal.class,
                    "req-cost-002"
            );
            assertThat(cost).isNotNull();
            assertThat(cost).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    @Test
    void noPricingRule_persistsNullCost() {
        var event = new UsageEvent(
                "req-cost-003", "tenant-cost", "unknown-model-xyz", "unknown-provider",
                100, 50, false, 300, Instant.now()
        );

        kafkaTemplate.send(UsageEventPublisher.USAGE_EVENTS_TOPIC, event.tenantId(), event);

        await().atMost(30, SECONDS).untilAsserted(() -> {
            var count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM usage_events WHERE request_id = ?",
                    Long.class, "req-cost-003"
            );
            assertThat(count).isEqualTo(1L);
        });

        var costIsNull = jdbcTemplate.queryForObject(
                "SELECT estimated_cost_usd IS NULL FROM usage_events WHERE request_id = ?",
                Boolean.class,
                "req-cost-003"
        );
        assertThat(costIsNull).isTrue();
    }

    @Test
    void usageQueryService_aggregatesCostCorrectly() {
        var now = Instant.now();
        // Two priced events + one cache hit
        kafkaTemplate.send(UsageEventPublisher.USAGE_EVENTS_TOPIC, "tenant-agg",
                new UsageEvent("req-agg-001", "tenant-agg", "gpt-4o-mini", "openai",
                        1_000_000, 0, false, 100, now));
        kafkaTemplate.send(UsageEventPublisher.USAGE_EVENTS_TOPIC, "tenant-agg",
                new UsageEvent("req-agg-002", "tenant-agg", "gpt-4o-mini", "openai",
                        0, 1_000_000, false, 100, now));
        kafkaTemplate.send(UsageEventPublisher.USAGE_EVENTS_TOPIC, "tenant-agg",
                new UsageEvent("req-agg-003", "tenant-agg", "gpt-4o-mini", "openai",
                        500, 500, true, 5, now));

        await().atMost(30, SECONDS).untilAsserted(() -> {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM usage_events WHERE tenant_id = 'tenant-agg'",
                    Long.class
            );
            assertThat(count).isEqualTo(3L);
        });

        var summary = usageQueryService.summarize(
                "tenant-agg",
                now.minusSeconds(60),
                now.plusSeconds(60)
        );

        // req-agg-001: 1M prompt * $0.15/1M = $0.15
        // req-agg-002: 1M completion * $0.60/1M = $0.60
        // req-agg-003: cache hit = $0.00
        // total = $0.75
        assertThat(summary.totalCostUsd()).isEqualByComparingTo(new BigDecimal("0.75000000"));
        assertThat(summary.requestsWithoutPricing()).isZero();
        assertThat(summary.byModel()).hasSize(1);
        assertThat(summary.byModel().getFirst().totalCostUsd())
                .isEqualByComparingTo(new BigDecimal("0.75000000"));
    }

    @Test
    void usageQueryService_requestsWithoutPricing_countedCorrectly() {
        Instant now = Instant.now();
        kafkaTemplate.send(UsageEventPublisher.USAGE_EVENTS_TOPIC, "tenant-unpriced",
                new UsageEvent("req-unpriced-001", "tenant-unpriced", "unknown-model", "unknown-provider",
                        100, 100, false, 200, now));

        await().atMost(30, SECONDS).untilAsserted(() -> {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM usage_events WHERE tenant_id = 'tenant-unpriced'",
                    Long.class
            );
            assertThat(count).isEqualTo(1L);
        });

        var summary = usageQueryService.summarize(
                "tenant-unpriced",
                now.minusSeconds(60),
                now.plusSeconds(60)
        );

        assertThat(summary.totalCostUsd()).isNull();
        assertThat(summary.requestsWithoutPricing()).isEqualTo(1L);
    }
}