package com.marcos.llmgateway.cache.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gateway.cache")
public record CacheProperties(double similarityThreshold) {}
