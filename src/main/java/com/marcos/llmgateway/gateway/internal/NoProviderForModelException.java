package com.marcos.llmgateway.gateway.internal;

public class NoProviderForModelException extends RuntimeException {
    public NoProviderForModelException(String message) {
        super(message);
    }
}
