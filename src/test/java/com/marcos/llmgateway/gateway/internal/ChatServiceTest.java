package com.marcos.llmgateway.gateway.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.marcos.llmgateway.cache.SemanticCache;
import com.marcos.llmgateway.gateway.ChatRequest;
import com.marcos.llmgateway.gateway.ChatResponse;
import com.marcos.llmgateway.gateway.LlmProvider;
import com.marcos.llmgateway.gateway.Message;
import com.marcos.llmgateway.gateway.ProviderException;
import com.marcos.llmgateway.gateway.Role;
import com.marcos.llmgateway.gateway.RoutingStrategy;
import com.marcos.llmgateway.gateway.Usage;
import com.marcos.llmgateway.gateway.internal.exceptions.AllProvidersFailedException;
import com.marcos.llmgateway.gateway.internal.exceptions.NoProviderForModelException;
import com.marcos.llmgateway.metering.UsageEvent;
import com.marcos.llmgateway.metering.UsageEventPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class ChatServiceTest {

    private LlmProvider providerA;
    private LlmProvider providerB;
    private SemanticCache cache;
    private UsageEventPublisher publisher;

    @BeforeEach
    void setUp() {
        providerA = mock(LlmProvider.class);
        providerB = mock(LlmProvider.class);
        cache = mock(SemanticCache.class);
        publisher = mock(UsageEventPublisher.class);

        when(providerA.name()).thenReturn("providerA");
        when(providerB.name()).thenReturn("providerB");
        when(providerA.supports("test-model")).thenReturn(true);
        when(providerB.supports("test-model")).thenReturn(true);
    }

    private ChatService newService() {
        var service = new ChatService(
                List.of(providerA, providerB),
                cache,
                new SimpleMeterRegistry(),
                publisher,
                new JsonMapper()  // ← Jackson 3 default mapper
        );
        service.registerMeters();
        return service;
    }

    private ChatRequest request(RoutingStrategy strategy) {
        return new ChatRequest(
                "test-model",
                List.of(new Message(Role.USER, "hi")),
                0.0,
                strategy,
                "alice-corp"
        );
    }

    private ChatResponse responseFrom(String provider) {
        return new ChatResponse(
                new Message(Role.ASSISTANT, "hello from " + provider),
                new Usage(5, 10, "test-model")
        );
    }

    @Test
    void cacheHit_returnsCachedResponseWithoutCallingProvider() {
        ChatResponse cached = responseFrom("cache");
        String json = new tools.jackson.databind.json.JsonMapper().writeValueAsString(cached);
        when(cache.lookup(any(), any())).thenReturn(Optional.of(json));

        var result = newService().chat(request(RoutingStrategy.SEQUENTIAL_FALLBACK));

        assertThat(result).isEqualTo(cached);
        verify(providerA, never()).chat(any());
        verify(providerB, never()).chat(any());
    }

    @Test
    void sequentialFallback_firstProviderSucceeds_secondNotCalled() {
        when(cache.lookup(any(), any())).thenReturn(Optional.empty());
        when(providerA.chat(any())).thenReturn(responseFrom("A"));

        var result = newService().chat(request(RoutingStrategy.SEQUENTIAL_FALLBACK));

        assertThat(result.message().content()).contains("from A");
        verify(providerB, never()).chat(any());
    }

    @Test
    void sequentialFallback_firstFails_secondSucceeds() {
        when(cache.lookup(any(), any())).thenReturn(Optional.empty());
        when(providerA.chat(any())).thenThrow(new ProviderException("A failed", null));
        when(providerB.chat(any())).thenReturn(responseFrom("B"));

        var result = newService().chat(request(RoutingStrategy.SEQUENTIAL_FALLBACK));

        assertThat(result.message().content()).contains("from B");
    }

    @Test
    void sequentialFallback_allFail_throwsAggregated() {
        when(cache.lookup(any(), any())).thenReturn(Optional.empty());
        when(providerA.chat(any())).thenThrow(new ProviderException("A failed", null));
        when(providerB.chat(any())).thenThrow(new ProviderException("B failed", null));

        assertThatThrownBy(() -> newService().chat(request(RoutingStrategy.SEQUENTIAL_FALLBACK)))
                .isInstanceOf(AllProvidersFailedException.class);
    }

    @Test
    void parallelRace_oneSucceedsOneFails_returnsSuccess() {
        when(cache.lookup(any(), any())).thenReturn(Optional.empty());
        when(providerA.chat(any())).thenThrow(new ProviderException("A failed", null));
        when(providerB.chat(any())).thenReturn(responseFrom("B"));

        var result = newService().chat(request(RoutingStrategy.PARALLEL_RACE));

        assertThat(result.message().content()).contains("from B");
    }

    @Test
    void parallelRace_allFail_throwsAggregated() {
        when(cache.lookup(any(), any())).thenReturn(Optional.empty());
        when(providerA.chat(any())).thenThrow(new ProviderException("A failed", null));
        when(providerB.chat(any())).thenThrow(new ProviderException("B failed", null));

        assertThatThrownBy(() -> newService().chat(request(RoutingStrategy.PARALLEL_RACE)))
                .isInstanceOf(AllProvidersFailedException.class);
    }

    @Test
    void noProviderSupportsModel_throws() {
        when(providerA.supports("test-model")).thenReturn(false);
        when(providerB.supports("test-model")).thenReturn(false);
        when(cache.lookup(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().chat(request(RoutingStrategy.SEQUENTIAL_FALLBACK)))
                .isInstanceOf(NoProviderForModelException.class);
    }

    @Test
    void successfulRequest_emitsUsageEvent() {
        when(cache.lookup(any(), any())).thenReturn(Optional.empty());
        when(providerA.chat(any())).thenReturn(responseFrom("A"));

        newService().chat(request(RoutingStrategy.SEQUENTIAL_FALLBACK));

        ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
        verify(publisher).publish(captor.capture());

        UsageEvent event = captor.getValue();
        assertThat(event.tenantId()).isEqualTo("alice-corp");
        assertThat(event.model()).isEqualTo("test-model");
        assertThat(event.provider()).isEqualTo("providerA");
        assertThat(event.cacheHit()).isFalse();
        assertThat(event.promptTokens()).isEqualTo(5);
        assertThat(event.completionTokens()).isEqualTo(10);
    }

    @Test
    void cacheHit_emitsUsageEventWithCacheProvider() {
        String json = new tools.jackson.databind.json.JsonMapper().writeValueAsString(responseFrom("cache"));
        when(cache.lookup(any(), any())).thenReturn(Optional.of(json));

        newService().chat(request(RoutingStrategy.SEQUENTIAL_FALLBACK));

        ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
        verify(publisher).publish(captor.capture());

        UsageEvent event = captor.getValue();
        assertThat(event.provider()).isEqualTo("cache");
        assertThat(event.cacheHit()).isTrue();
    }
}