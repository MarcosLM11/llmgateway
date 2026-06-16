package com.marcos.llmgateway.providers.ollama;

import com.marcos.llmgateway.gateway.ChatRequest;
import com.marcos.llmgateway.gateway.ChatResponse;
import com.marcos.llmgateway.gateway.LlmProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import static com.marcos.llmgateway.providers.ollama.OllamaMapper.toDTO;
import static com.marcos.llmgateway.providers.ollama.OllamaMapper.toDomain;

@Component
public class OllamaLlmProvider implements LlmProvider {

    private final RestClient restClient;

    public OllamaLlmProvider(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        var ollamaRequest = toDTO(request);
        var ollamaResponse = restClient.post()
                .uri("/api/chat")
                .body(ollamaRequest)
                .retrieve()
                .body(OllamaChatResponseDTO.class);
        if (ollamaResponse == null) {
            return null;
        }
        return toDomain(ollamaResponse);
    }
}
