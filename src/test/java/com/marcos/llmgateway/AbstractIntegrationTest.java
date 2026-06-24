package com.marcos.llmgateway;

import com.marcos.llmgateway.cache.EmbeddingService;
import com.marcos.llmgateway.cache.internal.StubEmbeddingService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(AbstractIntegrationTest.IntegrationTestConfig.class)
public abstract class AbstractIntegrationTest {

    // Singleton containers: started once per JVM, cleaned up by Ryuk on exit.
    // @Container is intentionally absent — it would stop/restart containers between
    // test classes, changing the port and invalidating the cached Spring context.
    @SuppressWarnings({"resource", "rawtypes"})
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres")
    );

    @SuppressWarnings("resource")
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
                    .asCompatibleSubstituteFor("confluentinc/cp-kafka")
    );

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @TestConfiguration
    static class IntegrationTestConfig {
        @Bean
        @Primary
        EmbeddingService stubEmbeddingService() {
            return new StubEmbeddingService();
        }
    }
}