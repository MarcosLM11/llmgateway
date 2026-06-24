package com.marcos.llmgateway.cache.internal;

import com.marcos.llmgateway.cache.EmbeddingService;
import com.marcos.llmgateway.cache.SemanticCache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.postgresql.util.PGobject;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class PgVectorSemanticCache implements SemanticCache {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private final MeterRegistry meterRegistry;
    private final CacheProperties cacheProperties;

    private Counter cacheHits;
    private Counter cacheMisses;

    public PgVectorSemanticCache(JdbcTemplate jdbcTemplate, EmbeddingService embeddingService, CacheProperties cacheProperties, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
        this.cacheProperties = cacheProperties;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void registerMeters() {
        this.cacheHits = Counter.builder("llmgateway.cache.lookups").tag("result","hit").register(meterRegistry);
        this.cacheMisses = Counter.builder("llmgateway.cache.lookups").tag("result","miss").register(meterRegistry);
    }

    @Override
    public Optional<String> lookup(String tenantId, String prompt) {
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

        Optional<String> result = jdbcTemplate.query(sql,
                rs -> {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(rs.getString("response"));
                },
                vector, tenantId, vector, cacheProperties.similarityThreshold()
        );

        if (result.isPresent()) {
            cacheHits.increment();
        } else {
            cacheMisses.increment();
        }

        return result;
    }

    @Override
    public void store(String tenantId, String prompt, String responseJson) {
        var embedding = embeddingService.embed(prompt);
        var vector = toVectorObject(embedding);

        var jsonb = new PGobject();
        try {
            jsonb.setType("jsonb");
            jsonb.setValue(responseJson);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }

        var sql = """
            INSERT INTO semantic_cache_entries
                (tenant_id, embedding, prompt, response)
            VALUES (?, ?, ?, ?)
            """;

        jdbcTemplate.update(sql, tenantId, vector, prompt, jsonb);
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
