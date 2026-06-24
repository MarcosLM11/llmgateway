package com.marcos.llmgateway.metering.internal;

import static org.assertj.core.api.Assertions.assertThat;
import com.marcos.llmgateway.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PricingRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private PricingRepository pricingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findRule_exactModelMatch_returnsExactRule() {
        Optional<PricingRule> rule = pricingRepository.findRule("openai", "gpt-4o-mini");

        assertThat(rule).isPresent();
        assertThat(rule.get().promptPerMillion()).isEqualByComparingTo(new BigDecimal("0.15"));
        assertThat(rule.get().completionPerMillion()).isEqualByComparingTo(new BigDecimal("0.60"));
    }

    @Test
    void findRule_wildcardFallback_returnsWildcardRule() {
        // Ollama has a wildcard '*' row; any specific model should match it
        Optional<PricingRule> rule = pricingRepository.findRule("ollama", "llama3.2");

        assertThat(rule).isPresent();
        assertThat(rule.get().promptPerMillion()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(rule.get().completionPerMillion()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void findRule_exactMatchPreferredOverWildcard() {
        // Insert a specific rule that should win over the wildcard
        jdbcTemplate.update(
                "INSERT INTO pricing_rules (provider, model, prompt_per_million, completion_per_million) " +
                "VALUES ('ollama', 'llama3-special', 1.00, 2.00)"
        );

        Optional<PricingRule> rule = pricingRepository.findRule("ollama", "llama3-special");

        assertThat(rule).isPresent();
        assertThat(rule.get().promptPerMillion()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(rule.get().completionPerMillion()).isEqualByComparingTo(new BigDecimal("2.00"));
    }

    @Test
    void findRule_unknownProviderAndModel_returnsEmpty() {
        Optional<PricingRule> rule = pricingRepository.findRule("unknown-provider", "unknown-model");

        assertThat(rule).isEmpty();
    }

    @Test
    void findRule_multipleEffectiveFrom_returnsLatest() {
        jdbcTemplate.update(
                "INSERT INTO pricing_rules (provider, model, prompt_per_million, completion_per_million, effective_from) " +
                "VALUES ('openai', 'gpt-future', 5.00, 20.00, '2020-01-01'::timestamptz)"
        );
        jdbcTemplate.update(
                "INSERT INTO pricing_rules (provider, model, prompt_per_million, completion_per_million, effective_from) " +
                "VALUES ('openai', 'gpt-future', 3.00, 12.00, '2025-01-01'::timestamptz)"
        );

        Optional<PricingRule> rule = pricingRepository.findRule("openai", "gpt-future");

        assertThat(rule).isPresent();
        // Latest effective_from (2025-01-01) wins
        assertThat(rule.get().promptPerMillion()).isEqualByComparingTo(new BigDecimal("3.00"));
    }
}