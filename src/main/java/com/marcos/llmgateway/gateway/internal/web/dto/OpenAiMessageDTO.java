package com.marcos.llmgateway.gateway.internal.web.dto;

public record OpenAiMessageDTO(
        String role,
        String content
) {
}
