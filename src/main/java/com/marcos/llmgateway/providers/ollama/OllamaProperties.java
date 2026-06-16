package com.marcos.llmgateway.providers.ollama;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gateway.providers.ollama")
public record OllamaProperties(
        String baseUrl
) {
}
