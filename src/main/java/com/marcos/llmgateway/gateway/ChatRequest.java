package com.marcos.llmgateway.gateway;

import java.util.List;

public record ChatRequest(
        String model,
        List<Message> messages,
        Double temperature
) {
}
