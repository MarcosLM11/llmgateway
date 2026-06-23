package com.marcos.llmgateway.cache.internal;

import com.marcos.llmgateway.cache.EmbeddingService;

public class StubEmbeddingService implements EmbeddingService {

    @Override
    public float[] embed(String text) {
        float[] vector = new float[768];
        if (text.contains("TOTALLY UNRELATED")) {
            // vector "opuesto": valor constante negativo
            for (int i = 0; i < 768; i++) vector[i] = -1.0f;
        } else {
            // hash determinista basado en el texto -> vector reproducible
            int hash = text.hashCode();
            for (int i = 0; i < 768; i++) {
                vector[i] = (float) Math.sin(hash + i * 0.01) * 0.1f;
            }
        }
        return vector;
    }
}
