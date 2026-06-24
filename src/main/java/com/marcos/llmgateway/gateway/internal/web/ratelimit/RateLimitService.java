package com.marcos.llmgateway.gateway.internal.web.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class RateLimitService {

    private final Cache<String, Bucket> buckets;
    private final RateLimitProperties rateLimitProperties;

    public RateLimitService(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofHours(1))
                .build();
    }

    public boolean tryConsume(String tenantId) {
        Bucket bucket = buckets.get(tenantId, this::newBucket);
        return bucket.tryConsume(1);
    }

    private Bucket newBucket(String tenantId) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(rateLimitProperties.capacity())
                .refillGreedy(
                        rateLimitProperties.refillTokens(),
                        Duration.ofSeconds(rateLimitProperties.refillPeriodSeconds()))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
