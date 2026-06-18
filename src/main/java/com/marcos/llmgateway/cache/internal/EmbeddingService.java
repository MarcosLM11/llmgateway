package com.marcos.llmgateway.cache.internal;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final MeterRegistry meterRegistry;
    private Timer embedTimer;

    public EmbeddingService(EmbeddingModel embeddingModel,  MeterRegistry meterRegistry) {
        this.embeddingModel = embeddingModel;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void registerMeters() {
        this.embedTimer = Timer.builder("llmgateway.embedding.latency")
                .description("Latency of embedding generation")
                .register(meterRegistry);
    }

    public float[] embed(String text) {
        return embedTimer.record(() -> embeddingModel.embed(text));
    }
}
