package com.marcos.llmgateway.metering.internal;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CostCalculatorTest {

    private final CostCalculator calculator = new CostCalculator();

    @Test
    void calculate_typicalPaidModel_returnsCorrectCost() {
        // gpt-4o: $2.50 prompt / $10.00 completion per 1M tokens
        var rule = new PricingRule(new BigDecimal("2.50"), new BigDecimal("10.00"));
        var cost = calculator.calculate(rule, 1000, 500);
        // prompt:  1000 / 1_000_000 * 2.50 = 0.0025
        // completion: 500 / 1_000_000 * 10.00 = 0.005
        // total = 0.0075
        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.00750000"));
    }

    @Test
    void calculate_freeModel_returnsZero() {
        var rule = new PricingRule(BigDecimal.ZERO, BigDecimal.ZERO);
        var cost = calculator.calculate(rule, 5000, 2000);
        assertThat(cost).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculate_oneMillionTokensEach_returnsExactRates() {
        var rule = new PricingRule(new BigDecimal("0.15"), new BigDecimal("0.60"));
        var cost = calculator.calculate(rule, 1_000_000, 1_000_000);
        // $0.15 + $0.60 = $0.75
        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.75000000"));
    }

    @Test
    void calculate_zeroTokens_returnsZero() {
        var rule = new PricingRule(new BigDecimal("10.00"), new BigDecimal("40.00"));
        var cost = calculator.calculate(rule, 0, 0);
        assertThat(cost).isEqualByComparingTo(BigDecimal.ZERO);
    }
}