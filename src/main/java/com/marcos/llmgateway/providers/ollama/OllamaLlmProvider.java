package com.marcos.llmgateway.providers.ollama;

import com.marcos.llmgateway.gateway.ChatRequest;
import com.marcos.llmgateway.gateway.ChatResponse;
import com.marcos.llmgateway.gateway.LlmProvider;
import com.marcos.llmgateway.gateway.ProviderException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static com.marcos.llmgateway.providers.ollama.OllamaMapper.toDTO;
import static com.marcos.llmgateway.providers.ollama.OllamaMapper.toDomain;

@Order(1)
@Component
public class OllamaLlmProvider implements LlmProvider {

    private final RestClient restClient;

    public OllamaLlmProvider(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        var ollamaRequest = toDTO(request);
        try {
            var ollamaResponse = restClient.post()
                    .uri("/api/chat")
                    .body(ollamaRequest)
                    .retrieve()
                    .body(OllamaChatResponseDTO.class);
            return toDomain(ollamaResponse);
        } catch (RestClientException e) {
            throw new ProviderException("Ollama call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(String model) {
        return model.startsWith("llama")
                || model.startsWith("qwen")
                || model.startsWith("mistral")
                || model.startsWith("phi")
                || model.startsWith("gemma");
    }
}
