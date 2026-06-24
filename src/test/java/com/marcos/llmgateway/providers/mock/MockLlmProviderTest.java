package com.marcos.llmgateway.providers.mock;

import static org.assertj.core.api.Assertions.assertThat;
import com.marcos.llmgateway.gateway.ChatRequest;
import com.marcos.llmgateway.gateway.Message;
import com.marcos.llmgateway.gateway.Role;
import com.marcos.llmgateway.gateway.RoutingStrategy;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockLlmProviderTest {

    @Test
    void supports_onlyConfiguredModels() {
        var provider = new MockLlmProvider(new MockProperties(true, List.of("mock-fast", "mock-slow"), 0.0, 0));

        assertThat(provider.supports("mock-fast")).isTrue();
        assertThat(provider.supports("mock-slow")).isTrue();
        assertThat(provider.supports("gpt-4o")).isFalse();
    }

    @Test
    void chat_withZeroFailRate_returnsMockResponse() {
        var provider = new MockLlmProvider(new MockProperties(true, List.of("mock-fast"), 0.0, 0));

        var response = provider.chat(new ChatRequest(
                "mock-fast",
                List.of(new Message(Role.USER, "hola")),
                0.0,
                RoutingStrategy.SEQUENTIAL_FALLBACK,
                "tenant-1"
        ));

        assertThat(response.message().role()).isEqualTo(Role.ASSISTANT);
        assertThat(response.message().content()).isEqualTo("Mock response for model: mock-fast");
        assertThat(response.usage().modelUsed()).isEqualTo("mock-fast");
        assertThat(response.usage().promptTokens()).isPositive();
        assertThat(response.usage().completionTokens()).isEqualTo(10);
    }

    @Test
    void name_returnsMock() {
        var provider = new MockLlmProvider(new MockProperties(true, List.of("mock-fast"), 0.0, 0));

        assertThat(provider.name()).isEqualTo("mock");
    }
}
