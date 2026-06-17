package com.marcos.llmgateway.providers.mock;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties("gateway.providers.mock")
public record MockProperties(
        boolean enabled,
        List<String> supportedModels,
        double failRate,
        int latencyMs
) {
}
