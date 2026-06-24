package com.marcos.llmgateway.cache.internal;

import com.marcos.llmgateway.AbstractIntegrationTest;
import com.marcos.llmgateway.cache.SemanticCache;
import com.marcos.llmgateway.gateway.ChatResponse;
import com.marcos.llmgateway.gateway.Message;
import com.marcos.llmgateway.gateway.Role;
import com.marcos.llmgateway.gateway.Usage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class PgVectorSemanticCacheIT extends AbstractIntegrationTest {

    @Autowired
    private SemanticCache cache;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanCache() {
        jdbcTemplate.update("TRUNCATE semantic_cache_entries");
    }

    @Test
    void cache_miss_when_empty() {
        Optional<String> result = cache.lookup("tenant-a", "Hello world");
        assertThat(result).isEmpty();
    }

    @Test
    void cache_hit_for_identical_prompt() {
        String json = "{\"message\":{\"role\":\"ASSISTANT\",\"content\":\"Hi there\"},\"usage\":{\"promptTokens\":5,\"completionTokens\":10,\"modelUsed\":\"mock-model\"}}";
        cache.store("tenant-a", "Hello world", json);

        Optional<String> result = cache.lookup("tenant-a", "Hello world");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("Hi there");
    }

    @Test
    void cache_miss_when_prompts_unrelated() {
        cache.store("tenant-a", "Hello world", "{\"any\":\"json\"}");

        Optional<String> result = cache.lookup("tenant-a", "TOTALLY UNRELATED");

        assertThat(result).isEmpty();
    }

    @Test
    void cache_isolated_per_tenant() {
        cache.store("tenant-a", "Hello world", "{\"content\":\"for A\"}");

        Optional<String> result = cache.lookup("tenant-b", "Hello world");

        assertThat(result).isEmpty();
    }

    private ChatResponse sampleResponse(String content) {
        return new ChatResponse(
                new Message(Role.ASSISTANT, content),
                new Usage(5, 10, "mock-model")
        );
    }
}