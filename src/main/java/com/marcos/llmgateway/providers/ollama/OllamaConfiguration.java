package com.marcos.llmgateway.providers.ollama;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OllamaConfiguration {

    @Bean(name="ollamaRestClient")
    RestClient ollamaRestClient() {
        return RestClient.builder()
                .baseUrl("http://localhost:11434")
                .build();
    }
}
