package com.marcos.llmgateway.gateway;

public record ChatResponse(
        Message message,
        Usage usage
) {
}
