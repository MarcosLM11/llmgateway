package com.marcos.llmgateway.gateway.internal.exceptions;

public class AllProvidersFailedException extends RuntimeException {
    public AllProvidersFailedException(String message) {
        super(message);
    }
}
