package com.marcos.llmgateway.metering.internal;

import com.marcos.llmgateway.metering.UsageEvent;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class UsageEventRepository {

    private static final String INSERT_SQL = """
        INSERT INTO usage_events
            (request_id, tenant_id, model, provider, prompt_tokens, completion_tokens,
             cache_hit, latency_ms, event_timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (request_id) DO NOTHING
        """;

    private final JdbcTemplate jdbcTemplate;

    UsageEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    boolean insertIfAbsent(UsageEvent event) {
        var rows = jdbcTemplate.update(INSERT_SQL,
                event.requestId(),
                event.tenantId(),
                event.model(),
                event.provider(),
                event.promptTokens(),
                event.completionTokens(),
                event.cacheHit(),
                event.latencyMs(),
                Timestamp.from(event.timestamp())
        );
        return rows > 0;
    }
}
