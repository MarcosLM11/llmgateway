package com.marcos.llmgateway.providers.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaMessageDTO(
        String role,
        String content
) {
}
