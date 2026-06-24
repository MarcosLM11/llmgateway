package com.marcos.llmgateway.metering.internal.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record UsageSummaryDTO(
        String tenantId,
        Instant periodFrom,
        Instant periodTo,
        long totalRequests,
        long cacheHits,
        double cacheHitRate,
        long totalPromptTokens,
        long totalCompletionTokens,
        long totalTokens,
        long avgLatencyMs,
        BigDecimal totalCostUsd,
        long requestsWithoutPricing,
        List<ModelUsageDTO> byModel
) {}