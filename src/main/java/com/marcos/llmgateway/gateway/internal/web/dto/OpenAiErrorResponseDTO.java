package com.marcos.llmgateway.gateway.internal.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenAiErrorResponseDTO(
        @JsonProperty("request_id")
        String requestId,
        OpenAiErrorDTO error
) {
}
