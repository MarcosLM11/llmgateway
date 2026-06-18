package com.marcos.llmgateway.gateway.internal;

import com.marcos.llmgateway.cache.SemanticCache;
import com.marcos.llmgateway.gateway.ChatRequest;
import com.marcos.llmgateway.gateway.ChatResponse;
import com.marcos.llmgateway.gateway.LlmProvider;
import com.marcos.llmgateway.gateway.ProviderException;
import com.marcos.llmgateway.gateway.internal.exceptions.AllProvidersFailedException;
import com.marcos.llmgateway.gateway.internal.exceptions.NoProviderForModelException;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.StructuredTaskScope;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("preview")
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final List<LlmProvider> llmProviders;
    private final SemanticCache cache;
    private final MeterRegistry meterRegistry;

    private final Map<String, Timer> successTimers = new HashMap<>();
    private final Map<String, Timer> failureTimers = new HashMap<>();
    private DistributionSummary tokensPrompt;
    private DistributionSummary tokensCompletion;

    public ChatService(List<LlmProvider> llmProviders,  SemanticCache cache,  MeterRegistry meterRegistry) {
        this.llmProviders = llmProviders;
        this.cache = cache;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void registerMeters() {
        for (var provider : llmProviders) {
            var name = provider.name();
            successTimers.put(name, Timer.builder("llmgateway.provider.latency")
                    .tag("provider",name)
                    .tag("status", "success")
                    .register(meterRegistry));
            failureTimers.put(name, Timer.builder("llmgateway.provider.latency")
                    .tag("provider",name)
                    .tag("status", "failure")
                    .register(meterRegistry));
            tokensPrompt = DistributionSummary.builder("llmgateway.tokens.consumed")
                    .tag("type", "prompt")
                    .register(meterRegistry);
            tokensCompletion = DistributionSummary.builder("llmgateway.tokens.consumed")
                    .tag("type", "completion")
                    .register(meterRegistry);
        }
    }

    public ChatResponse chat(ChatRequest request) {
        var cacheable = cacheEligible(request);
        var prompt = cacheable ? promptOf(request) : null;

        if (cacheable) {
            Optional<ChatResponse> cached = cache.lookup(request.tenantId(), prompt);
            if (cached.isPresent()) {
                log.info("Cache HIT for tenant={}", request.tenantId());
                return cached.get();
            }
        }

        ChatResponse response = executeWithProviders(request);

        if (cacheable) {
            cache.store(request.tenantId(), prompt, response);
            log.info("Cache MISS for tenant={}, stored", request.tenantId());
        }

        return response;
    }

    private boolean cacheEligible(ChatRequest request) {
        return request.temperature() == null || request.temperature() <= 0.2;
    }

    private ChatResponse executeWithProviders(ChatRequest request) {
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
                return measuredCall(candidate, request);
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
                subtasks.add(scope.fork(() -> measuredCall(candidate, request)));
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

    // Helper para medir la llamada a un provider:
    private ChatResponse measuredCall(LlmProvider provider, ChatRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            var response = provider.chat(request);
            tokensPrompt.record(response.usage().promptTokens());
            tokensCompletion.record(response.usage().completionTokens());
            sample.stop(successTimers.get(provider.name()));
            return response;
        } catch (RuntimeException e) {
            sample.stop(failureTimers.get(provider.name()));
            throw e;
        }
    }

    private String promptOf(ChatRequest request) {
        return request.messages().stream()
                .map(m -> m.role().name().toLowerCase() + ": " + m.content())
                .collect(Collectors.joining("\n"));
    }
}
