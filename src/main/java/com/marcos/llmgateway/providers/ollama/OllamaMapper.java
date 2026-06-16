package com.marcos.llmgateway.providers.ollama;

import com.marcos.llmgateway.gateway.*;

public class OllamaMapper {


    public static ChatResponse toDomain(OllamaChatResponseDTO response) {
        return new ChatResponse(
                new Message(
                        Role.valueOf(response.message().role().toUpperCase()),
                        response.message().content()
                ),
                new Usage(
                        response.promptEvalCount(),
                        response.evalCount(),
                        response.model()
                )
        );
    }

    public static OllamaChatRequestDTO toDTO(ChatRequest chatRequest) {
        var messages = chatRequest.messages().stream()
                .map(m -> new OllamaMessageDTO(
                        m.role().name().toLowerCase(),
                        m.content()
                ))
                .toList();

        return new OllamaChatRequestDTO(
                chatRequest.model(),
                messages,
                false
        );
    }
}
