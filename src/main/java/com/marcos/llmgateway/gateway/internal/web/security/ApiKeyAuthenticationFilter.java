package com.marcos.llmgateway.gateway.internal.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String TENANT_ID_KEY = "tenantId";
    private static final String BEARER_PREFIX = "Bearer ";
    private final SecurityProperties securityProperties;

    public ApiKeyAuthenticationFilter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String key = authHeader.substring(BEARER_PREFIX.length()).trim();
            securityProperties.findByKey(key).ifPresent(apiKey -> {
                List<GrantedAuthority> authorities = apiKey.admin()
                        ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        : List.of();
                var auth = new UsernamePasswordAuthenticationToken(
                        apiKey.tenantId(),
                        null,
                        authorities
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
                MDC.put(TENANT_ID_KEY, apiKey.tenantId());
            });
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TENANT_ID_KEY);
        }
    }
}
