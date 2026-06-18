package com.marcos.llmgateway.gateway.internal.web.security;

import com.marcos.llmgateway.gateway.internal.web.ratelimit.RateLimitFilter;
import com.marcos.llmgateway.gateway.internal.web.ratelimit.RateLimitService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityProperties properties,
            RateLimitService rateLimitService,
            ApiKeyAuthenticationEntryPoint entryPoint,
            ObjectMapper objectMapper) {

        var apiKeyFilter = new ApiKeyAuthenticationFilter(properties);
        var rateLimitFilter = new RateLimitFilter(rateLimitService, objectMapper);

        http.csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/**").permitAll())
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(rateLimitFilter, ApiKeyAuthenticationFilter.class)
            .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint));

        return http.build();
    }
}
