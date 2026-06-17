package com.marcos.llmgateway.gateway;

public class ProviderException extends RuntimeException {
    public ProviderException(String message,  Throwable cause) {
        super(message,  cause);
    }
}
