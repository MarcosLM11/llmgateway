package com.marcos.llmgateway.gateway.internal.web.security;

import com.marcos.llmgateway.gateway.internal.web.RequestIdFilter;
import com.marcos.llmgateway.gateway.internal.web.dto.OpenAiErrorDTO;
import com.marcos.llmgateway.gateway.internal.web.dto.OpenAiErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;

@Component
public class ApiKeyAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(@NonNull HttpServletRequest request,
                         HttpServletResponse response,
                         @NonNull AuthenticationException authException) throws IOException {

        var requestId = MDC.get(RequestIdFilter.REQUEST_ID_KEY);
        var error = new OpenAiErrorResponseDTO(
                requestId,
                new OpenAiErrorDTO(
                        "Missing or invalid API key",
                        "invalid_request_error",
                        null,
                        "invalid_api_key"
                )
        );

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
