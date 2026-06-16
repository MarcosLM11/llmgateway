package com.marcos.llmgateway.providers.ollama;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OllamaConfiguration {

    private final OllamaProperties ollamaProperties;

    public OllamaConfiguration(OllamaProperties ollamaProperties) {
        this.ollamaProperties = ollamaProperties;
    }

    @Bean(name="ollamaRestClient")
    RestClient ollamaRestClient() {
        return RestClient.builder()
                .baseUrl(ollamaProperties.baseUrl())
                .build();
    }
}
