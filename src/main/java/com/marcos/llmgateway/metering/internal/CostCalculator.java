package com.marcos.llmgateway.metering.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
class CostCalculator {

    private static final BigDecimal MILLION = new BigDecimal("1000000");

    BigDecimal calculate(PricingRule rule, int promptTokens, int completionTokens) {
        BigDecimal promptCost = rule.promptPerMillion()
                .multiply(BigDecimal.valueOf(promptTokens))
                .divide(MILLION, 8, RoundingMode.HALF_UP);
        BigDecimal completionCost = rule.completionPerMillion()
                .multiply(BigDecimal.valueOf(completionTokens))
                .divide(MILLION, 8, RoundingMode.HALF_UP);
        return promptCost.add(completionCost);
    }
}