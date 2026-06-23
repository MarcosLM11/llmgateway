package com.marcos.llmgateway.cache;

public interface EmbeddingService {
    float[] embed(String text);
}