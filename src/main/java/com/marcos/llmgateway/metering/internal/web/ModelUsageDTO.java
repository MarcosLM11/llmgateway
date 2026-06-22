package com.marcos.llmgateway.metering.internal.web;

public record ModelUsageDTO(
        String model,
        long requests,
        long totalTokens
) {}
