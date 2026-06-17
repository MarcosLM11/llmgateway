package com.marcos.llmgateway.gateway.internal.web.dto;

import java.util.List;

public record OpenAiChatResponseDTO(
        String id,
        String object,
        Long created,
        String model,
        List<OpenAiChoiceDTO> choices,
        OpenAiUsageDTO usage
) {
}
