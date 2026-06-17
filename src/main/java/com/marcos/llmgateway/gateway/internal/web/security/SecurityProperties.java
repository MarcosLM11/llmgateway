package com.marcos.llmgateway.gateway.internal.web.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;
import java.util.Optional;

@ConfigurationProperties("gateway.security")
public record SecurityProperties(
        List<ApiKeyEntry> apiKeys
) {
    public Optional<ApiKeyEntry> findByKey(String key) {
        return apiKeys.stream()
                .filter(e -> e.key().equals(key))
                .findFirst();
    }
}
