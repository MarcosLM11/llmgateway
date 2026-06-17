package com.marcos.llmgateway.gateway.internal.exceptions;

public class NoProviderForModelException extends RuntimeException {
    public NoProviderForModelException(String message) {
        super(message);
    }
}
