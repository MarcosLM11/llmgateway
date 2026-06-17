package com.marcos.llmgateway.gateway.internal.web.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OpenAiChoiceDTO(
        int index,
        OpenAiMessageDTO message,
        String finishReason
) {
}
