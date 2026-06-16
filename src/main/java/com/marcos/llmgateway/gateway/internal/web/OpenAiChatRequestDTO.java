package com.marcos.llmgateway.gateway.internal.web;

import java.util.List;

public record OpenAiChatRequestDTO(
        String model,
        List<OpenAiMessageDTO> messages,
        Double temperature
) {
}
