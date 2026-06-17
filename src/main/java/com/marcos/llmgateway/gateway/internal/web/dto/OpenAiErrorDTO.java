package com.marcos.llmgateway.gateway.internal.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiErrorDTO(
        String message,
        String type,
        String param, //Nullable
        String code
) {
}
