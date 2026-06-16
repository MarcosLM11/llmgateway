package com.marcos.llmgateway.providers.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OllamaChatResponseDTO(
        String model,
        OllamaMessageDTO message,
        int promptEvalCount,
        int evalCount
) {
}
