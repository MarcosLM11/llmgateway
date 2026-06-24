package com.marcos.llmgateway.providers.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gateway.providers.openai")
public record OpenAiProperties(
        String apiKey,
        String baseUrl
) {}