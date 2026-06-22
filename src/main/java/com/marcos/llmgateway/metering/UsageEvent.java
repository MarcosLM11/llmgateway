package com.marcos.llmgateway.metering;

import java.time.Instant;

public record UsageEvent(
        String requestId,
        String tenantId,
        String model,
        String provider,
        int promptTokens,
        int completionTokens,
        boolean cacheHit,
        long latencyMs,
        Instant timestamp
) {}
