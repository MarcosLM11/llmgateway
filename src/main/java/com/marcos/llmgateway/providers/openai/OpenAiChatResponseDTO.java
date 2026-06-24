package com.marcos.llmgateway.providers.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiChatResponseDTO(
        String id,
        String model,
        List<OpenAiChoiceDTO> choices,
        OpenAiUsageDTO usage
) {
}