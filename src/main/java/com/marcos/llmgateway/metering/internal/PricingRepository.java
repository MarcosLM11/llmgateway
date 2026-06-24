package com.marcos.llmgateway.metering.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PricingRepository {

    private static final String FIND_RULE_SQL = """
        SELECT prompt_per_million, completion_per_million
        FROM pricing_rules
        WHERE provider = ?
          AND (model = ? OR model = '*')
          AND effective_from <= NOW()
        ORDER BY
            CASE WHEN model = ? THEN 0 ELSE 1 END,
            effective_from DESC
        LIMIT 1
        """;

    private final JdbcTemplate jdbcTemplate;

    PricingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<PricingRule> findRule(String provider, String model) {
        List<PricingRule> results = jdbcTemplate.query(
                FIND_RULE_SQL,
                (rs, _) -> new PricingRule(
                        rs.getBigDecimal("prompt_per_million"),
                        rs.getBigDecimal("completion_per_million")
                ),
                provider, model, model
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }
}