package com.marcos.llmgateway.gateway.internal.web.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class ApiKeyComparator {

    private ApiKeyComparator() {}

    /**
     * Constant-time equality for secret strings.
     * MessageDigest.isEqual XORs all bytes without early exit.
     * Leaks whether lengths differ — acceptable for uniform-length keys.
     */
    static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}