package com.marcos.llmgateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import com.marcos.llmgateway.metering.internal.web.UsageSummaryDTO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@AutoConfigureTestRestTemplate
class E2EIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void endToEnd_requestFlowsThroughCache_provider_kafka_database_andAdmin() {
        // 1) POST chat completion as alice
        Map<String, Object> body = Map.of(
                "model", "mock-fast",
                "messages", List.of(Map.of("role", "user", "content", "hola e2e test"))
        );

        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setContentType(MediaType.APPLICATION_JSON);
        userHeaders.setBearerAuth("sk-test-alice");

        ResponseEntity<Map> chatResponse = restTemplate.exchange(
                "/v1/chat/completions",
                HttpMethod.POST,
                new HttpEntity<>(body, userHeaders),
                Map.class
        );

        assertThat(chatResponse.getStatusCode().is2xxSuccessful())
                .as("Body was: " + chatResponse.getBody())
                .isTrue();

        // 2) Wait for Kafka consumer to persist the event
        await().atMost(60, SECONDS).untilAsserted(() -> {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM usage_events WHERE tenant_id = 'alice-corp'",
                    Long.class
            );
            assertThat(count).isEqualTo(1L);
        });

        // 3) Verify the row contents
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT model, provider, cache_hit, prompt_tokens, completion_tokens " +
                        "FROM usage_events WHERE tenant_id = 'alice-corp'"
        );
        assertThat(row.get("model")).isEqualTo("mock-fast");
        assertThat(row.get("provider")).isEqualTo("mock");
        assertThat(row.get("cache_hit")).isEqualTo(false);
        assertThat((Integer) row.get("prompt_tokens")).isPositive();

        // 4) Admin endpoint reflects the usage
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth("sk-admin-master");

        ResponseEntity<UsageSummaryDTO> adminResponse = restTemplate.exchange(
                "/admin/usage?tenantId=alice-corp",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                UsageSummaryDTO.class
        );

        assertThat(adminResponse.getStatusCode().is2xxSuccessful()).isTrue();
        UsageSummaryDTO summary = adminResponse.getBody();
        assertThat(summary).isNotNull();
        assertThat(summary.totalRequests()).isEqualTo(1L);
        assertThat(summary.cacheHits()).isEqualTo(0L);
        assertThat(summary.byModel()).hasSize(1);
        assertThat(summary.byModel().get(0).model()).isEqualTo("mock-fast");
    }
}
