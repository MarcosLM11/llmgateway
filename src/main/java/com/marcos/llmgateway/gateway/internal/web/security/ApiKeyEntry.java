package com.marcos.llmgateway.gateway.internal.web.security;

public record ApiKeyEntry(
        String key,
        String tenantId,
        boolean admin
) {
}
