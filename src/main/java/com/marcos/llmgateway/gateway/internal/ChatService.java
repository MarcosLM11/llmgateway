package com.marcos.llmgateway.gateway.internal;

import com.marcos.llmgateway.gateway.ChatRequest;
import com.marcos.llmgateway.gateway.ChatResponse;
import com.marcos.llmgateway.gateway.LlmProvider;
import com.marcos.llmgateway.gateway.ProviderException;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChatService {

    private final List<LlmProvider> llmProviders;

    public ChatService(List<LlmProvider> llmProviders) {
        this.llmProviders = llmProviders;
    }

    public ChatResponse chat(ChatRequest request) {
        var candidates = llmProviders.stream()
                .filter(p -> p.supports(request.model()))
                .toList();

        if (candidates.isEmpty()) {
            throw new NoProviderForModelException("No provider supports model: " + request.model());
        }

        var failures = new ArrayList<ProviderException>();

        for (var candidate : candidates) {
            try {
                return candidate.chat(request);
            } catch (ProviderException e) {
                failures.add(e);
            }
        }

        var aggregated = new AllProvidersFailedException(
                "All " + candidates.size() + " providers failed for model: " + request.model()
        );
        failures.forEach(aggregated::addSuppressed);
        throw aggregated;
    }
}
