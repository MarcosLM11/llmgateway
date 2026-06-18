package com.marcos.llmgateway.cache.internal;

import com.marcos.llmgateway.cache.SemanticCache;
import com.marcos.llmgateway.gateway.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class PgVectorSemanticCacheIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private SemanticCache cache;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        EmbeddingService stubEmbeddingService() {
            return new StubEmbeddingService();
        }
    }

    @BeforeEach
    void cleanCache() {
        jdbcTemplate.update("TRUNCATE semantic_cache_entries");
    }

    @Test
    void cache_miss_when_empty() {
        Optional<ChatResponse> result = cache.lookup("tenant-a", "Hello world");
        assertThat(result).isEmpty();
    }

    @Test
    void cache_hit_for_identical_prompt() {
        ChatResponse response = sampleResponse("Hi there");
        cache.store("tenant-a", "Hello world", response);

        Optional<ChatResponse> result = cache.lookup("tenant-a", "Hello world");

        assertThat(result).isPresent();
        assertThat(result.get().message().content()).isEqualTo("Hi there");
    }

    @Test
    void cache_miss_when_prompts_unrelated() {
        cache.store("tenant-a", "Hello world", sampleResponse("Hi"));

        Optional<ChatResponse> result = cache.lookup("tenant-a", "TOTALLY UNRELATED");

        assertThat(result).isEmpty();
    }

    @Test
    void cache_isolated_per_tenant() {
        cache.store("tenant-a", "Hello world", sampleResponse("Response for A"));

        Optional<ChatResponse> result = cache.lookup("tenant-b", "Hello world");

        assertThat(result).isEmpty();
    }

    private ChatResponse sampleResponse(String content) {
        return new ChatResponse(
                new Message(Role.ASSISTANT, content),
                new Usage(5, 10, "mock-model")
        );
    }
}