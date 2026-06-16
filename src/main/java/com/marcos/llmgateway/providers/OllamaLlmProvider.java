package com.marcos.llmgateway.providers;

import com.marcos.llmgateway.gateway.ChatRequest;
import com.marcos.llmgateway.gateway.ChatResponse;
import com.marcos.llmgateway.gateway.LlmProvider;
import com.marcos.llmgateway.gateway.Message;
import com.marcos.llmgateway.gateway.Role;
import com.marcos.llmgateway.gateway.Usage;
import org.springframework.stereotype.Component;

@Component
public class OllamaLlmProvider implements LlmProvider {

    @Override
    public ChatResponse chat(ChatRequest request) {
        var message = new Message(Role.ASSISTANT, "hello from ollama stub");
        var usage = new Usage(0, 0, request.model());
        return new ChatResponse(message, usage);
    }
}
