package com.marcos.llmgateway.providers.openai;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.marcos.llmgateway.gateway.ChatRequest;
import com.marcos.llmgateway.gateway.Message;
import com.marcos.llmgateway.gateway.ProviderException;
import com.marcos.llmgateway.gateway.Role;
import com.marcos.llmgateway.gateway.RoutingStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import java.util.List;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiLlmProviderTest {

    private WireMockServer wireMock;
    private OpenAiLlmProvider provider;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        var restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .defaultHeader("Authorization", "Bearer sk-test")
                .build();

        provider = new OpenAiLlmProvider(restClient);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    // --- supports() ---

    @Test
    void supports_gptModel_returnsTrue() {
        assertThat(provider.supports("gpt-4o")).isTrue();
        assertThat(provider.supports("gpt-3.5-turbo")).isTrue();
        assertThat(provider.supports("gpt-4-turbo")).isTrue();
    }

    @Test
    void supports_oModels_returnsTrue() {
        assertThat(provider.supports("o1")).isTrue();
        assertThat(provider.supports("o1-mini")).isTrue();
        assertThat(provider.supports("o3")).isTrue();
        assertThat(provider.supports("o4-mini")).isTrue();
    }

    @Test
    void supports_chatgptModel_returnsTrue() {
        assertThat(provider.supports("chatgpt-4o-latest")).isTrue();
    }

    @Test
    void supports_ollamaModels_returnsFalse() {
        assertThat(provider.supports("llama3")).isFalse();
        assertThat(provider.supports("mistral")).isFalse();
        assertThat(provider.supports("qwen2")).isFalse();
        assertThat(provider.supports("gemma")).isFalse();
        assertThat(provider.supports("phi3")).isFalse();
    }

    @Test
    void supports_unknownModel_returnsFalse() {
        assertThat(provider.supports("claude-3-opus")).isFalse();
        assertThat(provider.supports("mock-fast")).isFalse();
    }

    // --- chat() happy path ---

    @Test
    void chat_validRequest_returnsAssistantMessage() {
        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(okJson("""
                        {
                          "id": "chatcmpl-abc",
                          "object": "chat.completion",
                          "created": 1714012800,
                          "model": "gpt-4o",
                          "choices": [{
                            "index": 0,
                            "message": { "role": "assistant", "content": "Hello!" },
                            "finish_reason": "stop"
                          }],
                          "usage": {
                            "prompt_tokens": 10,
                            "completion_tokens": 5,
                            "total_tokens": 15
                          }
                        }
                        """)));

        var response = provider.chat(chatRequest("gpt-4o", "Hi", 0.7));

        assertThat(response.message().content()).isEqualTo("Hello!");
        assertThat(response.message().role()).isEqualTo(Role.ASSISTANT);
        assertThat(response.usage().promptTokens()).isEqualTo(10);
        assertThat(response.usage().completionTokens()).isEqualTo(5);
        assertThat(response.usage().modelUsed()).isEqualTo("gpt-4o");
    }

    // --- serialization tests ---

    @Test
    void chat_propagatesModelAndTemperatureInRequest() {
        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(okJson(successResponse("gpt-4-turbo"))));

        provider.chat(chatRequest("gpt-4-turbo", "Test message", 0.5));

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-4-turbo")))
                .withRequestBody(matchingJsonPath("$.temperature", equalTo("0.5")))
                .withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("user")))
                .withRequestBody(matchingJsonPath("$.messages[0].content", equalTo("Test message"))));
    }

    // --- error handling ---

    @Test
    void chat_server500_throwsProviderException() {
        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(serverError()));

        assertThatThrownBy(() -> provider.chat(chatRequest("gpt-4o", "Hi", 0.7)))
                .isInstanceOf(ProviderException.class);
    }

    @Test
    void chat_server401_throwsProviderException() {
        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(unauthorized()));

        assertThatThrownBy(() -> provider.chat(chatRequest("gpt-4o", "Hi", 0.7)))
                .isInstanceOf(ProviderException.class);
    }

    @Test
    void name_returnsOpenai() {
        assertThat(provider.name()).isEqualTo("openai");
    }

    // --- helpers ---

    private ChatRequest chatRequest(String model, String content, double temperature) {
        return new ChatRequest(
                model,
                List.of(new Message(Role.USER, content)),
                temperature,
                RoutingStrategy.SEQUENTIAL_FALLBACK,
                "test-tenant"
        );
    }

    private String successResponse(String model) {
        return """
                {
                  "id": "chatcmpl-xyz",
                  "object": "chat.completion",
                  "created": 1714012800,
                  "model": "%s",
                  "choices": [{
                    "index": 0,
                    "message": { "role": "assistant", "content": "OK" },
                    "finish_reason": "stop"
                  }],
                  "usage": {
                    "prompt_tokens": 5,
                    "completion_tokens": 2,
                    "total_tokens": 7
                  }
                }
                """.formatted(model);
    }
}