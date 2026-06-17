package com.marcos.llmgateway.providers.mock;

import com.marcos.llmgateway.gateway.ChatRequest;
import com.marcos.llmgateway.gateway.ChatResponse;
import com.marcos.llmgateway.gateway.LlmProvider;
import com.marcos.llmgateway.gateway.Message;
import com.marcos.llmgateway.gateway.ProviderException;
import com.marcos.llmgateway.gateway.Role;
import com.marcos.llmgateway.gateway.Usage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(
        name="gateway.providers.mock.enabled",
        havingValue = "true"
)
@Order(100)
@Component
public class MockLlmProvider implements LlmProvider {

    private final MockProperties mockProperties;

    public MockLlmProvider(MockProperties mockProperties) {
        this.mockProperties = mockProperties;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        // Simulate latency
        if (mockProperties.latencyMs() > 0) {
            try {
                Thread.sleep(mockProperties.latencyMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ProviderException("Mock task cancelled during sleep", e);
            }
        }

        // Simulate failure based on failRate
        if (Math.random() < mockProperties.failRate()) {
            throw new ProviderException("Mock provider intentional failure", null);
        }

        // Return a mock response
        var message = new Message(Role.ASSISTANT, "Mock response for model: " + request.model());
        var usage = new Usage(
                request.messages().stream()
                        .mapToInt(m -> m.content().length())
                        .sum()/4,
                10,
                request.model());
        System.out.println("[MOCK] task finished at " + System.currentTimeMillis() + " for model: " + request.model());
        return new ChatResponse(message, usage);
    }

    @Override
    public boolean supports(String model) {
        return mockProperties.supportedModels().contains(model);
    }
}
