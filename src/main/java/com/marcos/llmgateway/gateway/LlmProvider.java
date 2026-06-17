package com.marcos.llmgateway.gateway;

public interface LlmProvider {

    ChatResponse chat(ChatRequest request);

    boolean supports(String model);
}
