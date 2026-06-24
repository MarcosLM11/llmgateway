package com.marcos.llmgateway.metering.internal;

import com.marcos.llmgateway.metering.internal.web.ModelUsageDTO;
import com.marcos.llmgateway.metering.internal.web.UsageSummaryDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UsageQueryService {

    private static final String SUMMARY_SQL = """
        SELECT
            COUNT(*)                                                        AS total_requests,
            COALESCE(SUM(CASE WHEN cache_hit THEN 1 ELSE 0 END), 0)        AS cache_hits,
            COALESCE(SUM(prompt_tokens), 0)                                 AS total_prompt_tokens,
            COALESCE(SUM(completion_tokens), 0)                             AS total_completion_tokens,
            COALESCE(ROUND(AVG(latency_ms))::bigint, 0)                    AS avg_latency_ms,
            SUM(estimated_cost_usd)                                         AS total_cost_usd,
            COUNT(*) FILTER (WHERE estimated_cost_usd IS NULL AND NOT cache_hit) AS requests_without_pricing
        FROM usage_events
        WHERE tenant_id = ? AND event_timestamp >= ? AND event_timestamp <= ?
        """;

    private static final String BY_MODEL_SQL = """
        SELECT
            model,
            COUNT(*)                                                 AS requests,
            COALESCE(SUM(prompt_tokens), 0)                          AS total_prompt_tokens,
            COALESCE(SUM(completion_tokens), 0)                      AS total_completion_tokens,
            COALESCE(SUM(prompt_tokens + completion_tokens), 0)      AS total_tokens,
            SUM(estimated_cost_usd)                                  AS total_cost_usd
        FROM usage_events
        WHERE tenant_id = ? AND event_timestamp >= ? AND event_timestamp <= ?
        GROUP BY model
        ORDER BY total_tokens DESC
        """;

    private final JdbcTemplate jdbcTemplate;

    public UsageQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UsageSummaryDTO summarize(String tenantId, Instant from, Instant to) {
        var fromTs = Timestamp.from(from);
        var toTs = Timestamp.from(to);

        var summary = jdbcTemplate.queryForObject(SUMMARY_SQL, (rs, _) -> {
            var totalRequests = rs.getLong("total_requests");
            var cacheHits = rs.getLong("cache_hits");
            var totalPromptTokens = rs.getLong("total_prompt_tokens");
            var totalCompletionTokens = rs.getLong("total_completion_tokens");
            var avgLatencyMs = rs.getLong("avg_latency_ms");
            var hitRate = totalRequests > 0 ? (double) cacheHits / totalRequests : 0.0;
            var totalCostUsd = rs.getBigDecimal("total_cost_usd");
            var requestsWithoutPricing = rs.getLong("requests_without_pricing");
            return new Aggregates(totalRequests, cacheHits, hitRate,
                    totalPromptTokens, totalCompletionTokens, avgLatencyMs,
                    totalCostUsd, requestsWithoutPricing);
        }, tenantId, fromTs, toTs);

        List<ModelUsageDTO> byModel = jdbcTemplate.query(BY_MODEL_SQL, (rs, _) -> {
            var requests = rs.getLong("requests");
            var totalCostUsd = rs.getBigDecimal("total_cost_usd");
            var avgCostPerRequest = totalCostUsd != null
                    ? totalCostUsd.divide(BigDecimal.valueOf(requests), 8, RoundingMode.HALF_UP)
                    : null;
            return new ModelUsageDTO(
                    rs.getString("model"),
                    requests,
                    rs.getLong("total_prompt_tokens"),
                    rs.getLong("total_completion_tokens"),
                    rs.getLong("total_tokens"),
                    totalCostUsd,
                    avgCostPerRequest
            );
        }, tenantId, fromTs, toTs);

        return new UsageSummaryDTO(
                tenantId, from, to,
                summary.totalRequests(), summary.cacheHits(), summary.hitRate(),
                summary.totalPromptTokens(), summary.totalCompletionTokens(),
                summary.totalPromptTokens() + summary.totalCompletionTokens(),
                summary.avgLatencyMs(),
                summary.totalCostUsd(),
                summary.requestsWithoutPricing(),
                byModel
        );
    }

    private record Aggregates(
            long totalRequests, long cacheHits, double hitRate,
            long totalPromptTokens, long totalCompletionTokens, long avgLatencyMs,
            BigDecimal totalCostUsd, long requestsWithoutPricing
    ) {}
}