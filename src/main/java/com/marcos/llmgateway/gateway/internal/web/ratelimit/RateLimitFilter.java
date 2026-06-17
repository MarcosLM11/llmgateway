package com.marcos.llmgateway.gateway.internal.web.ratelimit;

import com.marcos.llmgateway.gateway.internal.web.RequestIdFilter;
import com.marcos.llmgateway.gateway.internal.web.dto.OpenAiErrorDTO;
import com.marcos.llmgateway.gateway.internal.web.dto.OpenAiErrorResponseDTO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

public class RateLimitFilter extends OncePerRequestFilter {

    private ObjectMapper objectMapper;
    private RateLimitService rateLimitService;

    public RateLimitFilter(RateLimitService rateLimitService,  ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String tenantId
                && !tenantId.equals("anonymousUser")) {
            if (!rateLimitService.tryConsume(tenantId)) {
                writeRateLimitError(response);// 429
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void writeRateLimitError(HttpServletResponse response) throws IOException {
        var requestId = MDC.get(RequestIdFilter.REQUEST_ID_KEY);
        var error = new OpenAiErrorResponseDTO(
                requestId,
                new OpenAiErrorDTO(
                        "Rate limit exceeded",
                        "api_error",
                        null,
                        "rate_limit_exceeded"
                )
        );
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
