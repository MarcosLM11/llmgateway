package com.marcos.llmgateway.providers.openai;

import com.marcos.llmgateway.gateway.ChatRequest;
import com.marcos.llmgateway.gateway.ChatResponse;
import com.marcos.llmgateway.gateway.Message;
import com.marcos.llmgateway.gateway.Role;
import com.marcos.llmgateway.gateway.Usage;

public class OpenAiMapper {

    public static OpenAiChatRequestDTO toDTO(ChatRequest request) {
        var messages = request.messages().stream()
                .map(m -> new OpenAiMessageDTO(m.role().name().toLowerCase(), m.content()))
                .toList();
        return new OpenAiChatRequestDTO(request.model(), messages, request.temperature());
    }

    public static ChatResponse toDomain(OpenAiChatResponseDTO response) {
        var choice = response.choices().get(0);
        var message = new Message(
                Role.valueOf(choice.message().role().toUpperCase()),
                choice.message().content()
        );
        var usage = new Usage(
                response.usage().promptTokens(),
                response.usage().completionTokens(),
                response.model()
        );
        return new ChatResponse(message, usage);
    }
}