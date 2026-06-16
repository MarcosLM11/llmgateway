package com.marcos.llmgateway.gateway;

public record Message(
        Role role,
        String content
) {
}
