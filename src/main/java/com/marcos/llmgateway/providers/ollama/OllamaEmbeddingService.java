package com.marcos.llmgateway.providers.ollama;

import com.marcos.llmgateway.cache.EmbeddingService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class OllamaEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final MeterRegistry meterRegistry;
    private Timer embedTimer;

    public OllamaEmbeddingService(EmbeddingModel embeddingModel, MeterRegistry meterRegistry) {
        this.embeddingModel = embeddingModel;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void registerMeters() {
        this.embedTimer = Timer.builder("llmgateway.embedding.latency")
                .description("Latency of embedding generation")
                .register(meterRegistry);
    }

    @Override
    public float[] embed(String text) {
        return embedTimer.record(() -> embeddingModel.embed(text));
    }
}
