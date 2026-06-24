package com.marcos.llmgateway.gateway.internal.web;

import com.marcos.llmgateway.gateway.internal.exceptions.AllProvidersFailedException;
import com.marcos.llmgateway.gateway.internal.exceptions.InvalidStrategyException;
import com.marcos.llmgateway.gateway.internal.exceptions.NoProviderForModelException;
import com.marcos.llmgateway.gateway.internal.web.dto.OpenAiErrorDTO;
import com.marcos.llmgateway.gateway.internal.web.dto.OpenAiErrorResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GatewayExceptionHandler {

    private static final String INVALID_REQUEST_ERROR = "invalid_request_error";

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    @ExceptionHandler(NoProviderForModelException.class)
    public ResponseEntity<OpenAiErrorResponseDTO> handleNoProvider(NoProviderForModelException e) {
        var requestId = MDC.get(RequestIdFilter.REQUEST_ID_KEY);
        var error = new OpenAiErrorResponseDTO(
                requestId,
                new OpenAiErrorDTO(
                        e.getMessage(),
                        INVALID_REQUEST_ERROR,
                        "model",
                        "model_not_found"
                )
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AllProvidersFailedException.class)
    public ResponseEntity<OpenAiErrorResponseDTO> handleAllFailed(AllProvidersFailedException e) {
        var requestId = MDC.get(RequestIdFilter.REQUEST_ID_KEY);
        var error = new OpenAiErrorResponseDTO(
                requestId,
                new OpenAiErrorDTO(
                        e.getMessage(),
                        "api_error",
                        null,
                        "all_providers_failed"
                )
        );
        return new ResponseEntity<>(error, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<OpenAiErrorResponseDTO> handleBadArgument(IllegalArgumentException e) {
        var requestId = MDC.get(RequestIdFilter.REQUEST_ID_KEY);
        var error = new OpenAiErrorResponseDTO(
                requestId,
                new OpenAiErrorDTO(
                        e.getMessage(),
                        INVALID_REQUEST_ERROR,
                        null,
                        "invalid_input"
                )
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidStrategyException.class)
    public ResponseEntity<OpenAiErrorResponseDTO> handleInvalidStrategy(InvalidStrategyException e) {
        var requestId = MDC.get(RequestIdFilter.REQUEST_ID_KEY);
        var error = new OpenAiErrorResponseDTO(
                requestId,
                new OpenAiErrorDTO(
                        e.getMessage(),
                        INVALID_REQUEST_ERROR,
                        "X-Gateway-Strategy",
                        "invalid_strategy"
                )
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<OpenAiErrorResponseDTO> handleNotReadable(HttpMessageNotReadableException e) {
        var requestId = MDC.get(RequestIdFilter.REQUEST_ID_KEY);
        var error = new OpenAiErrorResponseDTO(
                requestId,
                new OpenAiErrorDTO(
                        "Invalid JSON in request body",
                        INVALID_REQUEST_ERROR,
                        null,
                        "invalid_json"
                )
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<OpenAiErrorResponseDTO> handleGeneric(Exception e) {
        log.error("Unexpected error", e);
        var requestId = MDC.get(RequestIdFilter.REQUEST_ID_KEY);
        var error = new OpenAiErrorResponseDTO(
                requestId,
                new OpenAiErrorDTO(
                        e.getMessage(),
                        "api_error",
                        null,
                        "internal_error"
                )
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
