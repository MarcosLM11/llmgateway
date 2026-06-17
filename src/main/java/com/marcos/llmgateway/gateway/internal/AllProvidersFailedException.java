package com.marcos.llmgateway.gateway.internal;

public class AllProvidersFailedException extends RuntimeException {
    public AllProvidersFailedException(String message) {
        super(message);
    }
}
