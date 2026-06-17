package com.marcos.llmgateway.gateway.internal.web;

import com.marcos.llmgateway.gateway.ChatRequest;
import com.marcos.llmgateway.gateway.ChatResponse;
import com.marcos.llmgateway.gateway.Message;
import com.marcos.llmgateway.gateway.RoutingStrategy;
import com.marcos.llmgateway.gateway.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OpenAiChatMapper {

    public static ChatRequest toDomain(OpenAiChatRequestDTO request, RoutingStrategy strategy) {
        var chatMessages = request.messages().stream()
                .map(m -> new Message(Role.valueOf(m.role().toUpperCase()), m.content()))
                .toList();
        return new ChatRequest(
                request.model(),
                chatMessages,
                request.temperature(),
                strategy
        );
    }

    public static OpenAiChatResponseDTO toDTO(ChatResponse chatResponse) {
        return new OpenAiChatResponseDTO(
                UUID.randomUUID().toString(),
                "chat.completion",
                Instant.now().getEpochSecond(),
                chatResponse.usage().modelUsed(),
                List.of(new OpenAiChoiceDTO(
                        0,
                        new OpenAiMessageDTO(
                                chatResponse.message().role().toString().toLowerCase(),
                                chatResponse.message().content()
                        ),
                        "stop"
                        )
                ),
                new OpenAiUsageDTO(
                        chatResponse.usage().promptTokens(),
                        chatResponse.usage().completionTokens(),
                        chatResponse.usage().promptTokens() + chatResponse.usage().completionTokens()
                )
        );
    }
}
