package com.marcos.llmgateway.gateway;

public record Usage(
        int promptTokens,
        int completionTokens,
        String modelUsed
) {
}
