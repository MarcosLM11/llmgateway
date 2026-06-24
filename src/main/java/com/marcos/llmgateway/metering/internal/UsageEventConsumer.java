package com.marcos.llmgateway.metering.internal;

import com.marcos.llmgateway.metering.UsageEvent;
import com.marcos.llmgateway.metering.UsageEventPublisher;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
class UsageEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UsageEventConsumer.class);

    private final UsageEventRepository repository;
    private final PricingRepository pricingRepository;
    private final CostCalculator costCalculator;

    UsageEventConsumer(UsageEventRepository repository,
                       PricingRepository pricingRepository,
                       CostCalculator costCalculator) {
        this.repository = repository;
        this.pricingRepository = pricingRepository;
        this.costCalculator = costCalculator;
    }

    @KafkaListener(
            topics = UsageEventPublisher.USAGE_EVENTS_TOPIC,
            groupId = "llmgateway-metering"
    )
    void consume(UsageEvent event, Acknowledgment ack) {
        try {
            var cost = resolveCost(event);
            var inserted = repository.insertIfAbsent(event, cost);
            if (inserted) {
                log.info("Persisted UsageEvent requestId={} tenant={} cost={}",
                        event.requestId(), event.tenantId(), cost);
            } else {
                log.debug("Duplicate UsageEvent ignored requestId={}", event.requestId());
            }
            ack.acknowledge();
        } catch (DataAccessException e) {
            // No acknowledge → Kafka redeliver
            throw new IllegalStateException(
                    "Failed to persist UsageEvent requestId=" + event.requestId(),
                    e
            );
        }
    }

    private BigDecimal resolveCost(UsageEvent event) {
        if (event.cacheHit()) {
            return BigDecimal.ZERO;
        }
        return pricingRepository
                .findRule(event.provider(), event.model())
                .map(rule -> costCalculator.calculate(rule, event.promptTokens(), event.completionTokens()))
                .orElse(null);
    }
}