package com.marcos.llmgateway.cache.internal;

import com.marcos.llmgateway.cache.SemanticCache;
import com.marcos.llmgateway.gateway.ChatResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class PgVectorSemanticCache implements SemanticCache {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final CacheProperties cacheProperties;

    public PgVectorSemanticCache(JdbcTemplate jdbcTemplate, EmbeddingService embeddingService, ObjectMapper objectMapper,  CacheProperties cacheProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public Optional<ChatResponse> lookup(String tenantId, String prompt) {
        var embedding = embeddingService.embed(prompt);
        var vector = toVectorObject(embedding);
        var sql = """
                SELECT response, embedding <=> ? AS distance
                FROM semantic_cache_entries
                WHERE tenant_id = ?
                    AND embedding <=> ? < ?
                ORDER BY distance
                LIMIT 1
                """;

        return jdbcTemplate.query(sql,
                rs -> {
                    if (!rs.next()) return Optional.empty();
                    String json = rs.getString("response");
                    try {
                        return Optional.of(objectMapper.readValue(json, ChatResponse.class));
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to deserialize cached response", e);
                    }
                },
                vector, tenantId, vector, cacheProperties.similarityThreshold()
        );
    }

    @Override
    public void store(String tenantId, String prompt, ChatResponse response) {
        var embedding = embeddingService.embed(prompt);
        var vector = toVectorObject(embedding);
        String json;
        try {
            json = objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize response", e);
        }

        var jsonb = new PGobject();
        try {
            jsonb.setType("jsonb");
            jsonb.setValue(json);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }

        var sql = """
            INSERT INTO semantic_cache_entries
                (tenant_id, embedding, prompt, response, model_used)
            VALUES (?, ?, ?, ?, ?)
            """;

        jdbcTemplate.update(sql, tenantId, vector, prompt, jsonb, response.usage().modelUsed());
    }

    private PGobject toVectorObject(float[] embedding) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        var obj = new PGobject();
        try {
            obj.setType("vector");
            obj.setValue(sb.toString());
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return obj;
    }
}
