package com.marcos.llmgateway.providers.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiChoiceDTO(
        int index,
        OpenAiMessageDTO message,
        @JsonProperty("finish_reason") String finishReason
) {
}