package com.marcos.llmgateway.metering.internal.web;

import java.math.BigDecimal;

public record ModelUsageDTO(
        String model,
        long requests,
        long totalPromptTokens,
        long totalCompletionTokens,
        long totalTokens,
        BigDecimal totalCostUsd,
        BigDecimal avgCostPerRequestUsd
) {}