package com.marcos.llmgateway.providers.ollama;

import java.util.List;

public record OllamaChatRequestDTO(
        String model,
        List<OllamaMessageDTO> messages,
        boolean stream
) {
}
