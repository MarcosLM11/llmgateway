package com.marcos.llmgateway.gateway.internal.web.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gateway.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int capacity,
        int refillTokens,
        int refillPeriodSeconds
) {
}
