package com.marcos.llmgateway.gateway.internal;

import com.marcos.llmgateway.gateway.LlmProvider;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final LlmProvider llmProvider;

    public ChatService(LlmProvider llmProvider) {
        this.llmProvider = llmProvider;
    }


}
