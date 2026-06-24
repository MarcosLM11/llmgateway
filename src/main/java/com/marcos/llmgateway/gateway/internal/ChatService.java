package com.marcos.llmgateway.gateway.internal;

import com.marcos.llmgateway.cache.SemanticCache;
import com.marcos.llmgateway.gateway.ChatRequest;
import com.marcos.llmgateway.gateway.ChatResponse;
import com.marcos.llmgateway.gateway.LlmProvider;
import com.marcos.llmgateway.gateway.ProviderException;
import com.marcos.llmgateway.gateway.internal.exceptions.AllProvidersFailedException;
import com.marcos.llmgateway.gateway.internal.exceptions.NoProviderForModelException;
import com.marcos.llmgateway.metering.UsageEvent;
import com.marcos.llmgateway.metering.UsageEventPublisher;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
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
    private final UsageEventPublisher usageEventPublisher;
    private final ObjectMapper objectMapper;

    private final Map<String, Timer> successTimers = new HashMap<>();
    private final Map<String, Timer> failureTimers = new HashMap<>();
    private DistributionSummary tokensPrompt;
    private DistributionSummary tokensCompletion;

    public ChatService(List<LlmProvider> llmProviders,  SemanticCache cache,  MeterRegistry meterRegistry, UsageEventPublisher usageEventPublisher,  ObjectMapper objectMapper) {
        this.llmProviders = llmProviders;
        this.cache = cache;
        this.meterRegistry = meterRegistry;
        this.usageEventPublisher = usageEventPublisher;
        this.objectMapper = objectMapper;
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
        long start = System.nanoTime();
        var cacheable = cacheEligible(request);
        var prompt = cacheable ? promptOf(request) : null;

        if (cacheable) {
            Optional<String> cached = cache.lookup(request.tenantId(), prompt);
            if (cached.isPresent()) {
                log.info("Cache HIT for tenant={}", request.tenantId());
                var response = deserialize(cached.get());
                emitUsageEvent(request, response, "cache", true, start);
                return response;
            }
        }

        ChatOutcomeDTO outcome = executeWithProviders(request);

        if (cacheable) {
            cache.store(request.tenantId(), prompt, serialize(outcome.response()));
            log.info("Cache MISS for tenant={}, stored", request.tenantId());
        }

        emitUsageEvent(request, outcome.response(), outcome.providerName(), false, start);
        return outcome.response();
    }

    private String serialize(ChatResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize ChatResponse for cache", e);
        }
    }

    private ChatResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, ChatResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize ChatResponse from cache", e);
        }
    }

    private boolean cacheEligible(ChatRequest request) {
        return request.temperature() == null || request.temperature() <= 0.2;
    }

    private ChatOutcomeDTO executeWithProviders(ChatRequest request) {
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

    private ChatOutcomeDTO executeSequential(List<LlmProvider> candidates, ChatRequest request) {
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

    private ChatOutcomeDTO executeRace(List<LlmProvider> candidates, ChatRequest request) {
        List<StructuredTaskScope.Subtask<ChatOutcomeDTO>> subtasks = new ArrayList<>();
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<ChatOutcomeDTO>anySuccessfulOrThrow())) {
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
    private ChatOutcomeDTO measuredCall(LlmProvider provider, ChatRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            var response = provider.chat(request);
            tokensPrompt.record(response.usage().promptTokens());
            tokensCompletion.record(response.usage().completionTokens());
            sample.stop(successTimers.get(provider.name()));
            return new ChatOutcomeDTO(response, provider.name());
        } catch (RuntimeException e) {
            sample.stop(failureTimers.get(provider.name()));
            throw e;
        }
    }

    private void emitUsageEvent(ChatRequest request, ChatResponse response, String providerName, boolean cacheHit, long startNanos) {
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
        String requestId = org.slf4j.MDC.get("requestId");

        if (requestId == null) {
            requestId = java.util.UUID.randomUUID().toString();
        }

        var event = new UsageEvent(
                requestId,
                request.tenantId(),
                request.model(),
                providerName,
                response.usage().promptTokens(),
                response.usage().completionTokens(),
                cacheHit,
                latencyMs,
                java.time.Instant.now()
        );

        usageEventPublisher.publish(event);
    }

    private String promptOf(ChatRequest request) {
        return request.messages().stream()
                .map(m -> m.role().name().toLowerCase() + ": " + m.content())
                .collect(Collectors.joining("\n"));
    }
}
