package com.marcos.llmgateway.providers.openai;

import java.util.List;

public record OpenAiChatRequestDTO(
        String model,
        List<OpenAiMessageDTO> messages,
        Double temperature
) {
}