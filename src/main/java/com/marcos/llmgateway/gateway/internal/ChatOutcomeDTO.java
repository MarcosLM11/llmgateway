package com.marcos.llmgateway.gateway.internal;

import com.marcos.llmgateway.gateway.ChatResponse;

public record ChatOutcomeDTO(ChatResponse response, String providerName) {}
