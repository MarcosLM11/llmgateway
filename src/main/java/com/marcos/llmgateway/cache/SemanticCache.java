package com.marcos.llmgateway.cache;

import java.util.Optional;

public interface SemanticCache {
    Optional<String> lookup(String tenantId, String prompt);
    void store(String tenantId, String prompt, String response);
}
