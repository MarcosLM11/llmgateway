package com.marcos.llmgateway.gateway.internal;

import com.marcos.llmgateway.gateway.ChatRequest;
import com.marcos.llmgateway.gateway.ChatResponse;
import com.marcos.llmgateway.gateway.LlmProvider;
import com.marcos.llmgateway.gateway.ProviderException;
import com.marcos.llmgateway.gateway.internal.exceptions.AllProvidersFailedException;
import com.marcos.llmgateway.gateway.internal.exceptions.NoProviderForModelException;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

@Service
@SuppressWarnings("preview")
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

        return switch (request.strategy()) {
            case SEQUENTIAL_FALLBACK -> executeSequential(candidates, request);
            case PARALLEL_RACE -> executeRace(candidates, request);
        };
    }

    private ChatResponse executeSequential(List<LlmProvider> candidates, ChatRequest request) {
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

    private ChatResponse executeRace(List<LlmProvider> candidates, ChatRequest request) {
        List<StructuredTaskScope.Subtask<ChatResponse>> subtasks = new ArrayList<>();

        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<ChatResponse>anySuccessfulOrThrow())) {
            for (var candidate : candidates) {
                subtasks.add(scope.fork(() -> candidate.chat(request)));
            }

            return scope.join();
        } catch (StructuredTaskScope.FailedException _) {
            var aggregated = new AllProvidersFailedException(
                    "All " + candidates.size() + " providers failed for model: " + request.model()
            );
            subtasks.stream()
                    .filter(st -> st.state() == StructuredTaskScope.Subtask.State.FAILED)
                    .map(StructuredTaskScope.Subtask::exception)
                    .forEach(aggregated::addSuppressed);
            throw aggregated;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Chat execution was interrupted", e);
        }
    }
}
