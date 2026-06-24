package com.marcos.llmgateway.providers.openai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnExpression("!'${gateway.providers.openai.api-key:}'.isEmpty()")
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiConfiguration {

    private final OpenAiProperties properties;

    public OpenAiConfiguration(OpenAiProperties properties) {
        this.properties = properties;
    }

    @Bean(name = "openAiRestClient")
    RestClient openAiRestClient() {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}