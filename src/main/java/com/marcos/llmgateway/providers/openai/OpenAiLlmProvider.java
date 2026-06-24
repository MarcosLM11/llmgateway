package com.marcos.llmgateway.providers.openai;

import com.marcos.llmgateway.gateway.ChatRequest;
import com.marcos.llmgateway.gateway.ChatResponse;
import com.marcos.llmgateway.gateway.LlmProvider;
import com.marcos.llmgateway.gateway.ProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Order(2)
@Component
@ConditionalOnExpression("!'${gateway.providers.openai.api-key:}'.isEmpty()")
public class OpenAiLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmProvider.class);

    private final RestClient restClient;

    public OpenAiLlmProvider(@Qualifier("openAiRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        var openAiRequest = OpenAiMapper.toDTO(request);
        try {
            var response = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(openAiRequest)
                    .retrieve()
                    .body(OpenAiChatResponseDTO.class);
            return OpenAiMapper.toDomain(response);
        } catch (RestClientException e) {
            log.error("OpenAI call failed", e);
            throw new ProviderException("OpenAI call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(String model) {
        return model.startsWith("gpt-")
                || model.startsWith("o1")
                || model.startsWith("o3")
                || model.startsWith("o4")
                || model.startsWith("chatgpt-");
    }

    @Override
    public String name() {
        return "openai";
    }
}