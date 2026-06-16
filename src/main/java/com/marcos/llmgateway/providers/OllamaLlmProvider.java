package com.marcos.llmgateway.providers;

import com.marcos.llmgateway.gateway.ChatRequest;
import com.marcos.llmgateway.gateway.ChatResponse;
import com.marcos.llmgateway.gateway.LlmProvider;
import org.springframework.stereotype.Component;

@Component
public class OllamaLlmProvider implements LlmProvider {

    @Override
    public ChatResponse chat(ChatRequest request) {
        //TODO: implement function
        return null;
    }
}
