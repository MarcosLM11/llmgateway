package com.marcos.llmgateway.gateway.internal.web;

public record OpenAiMessageDTO(
        String role,
        String content
) {
}
