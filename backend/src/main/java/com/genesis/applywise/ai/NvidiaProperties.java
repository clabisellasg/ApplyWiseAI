package com.genesis.applywise.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "ai.nvidia")
public record NvidiaProperties(
        String apiKey,
        URI baseUrl,
        String model,
        int timeoutSeconds,
        int maxTokens,
        int maxInputCharacters
) {
    public NvidiaProperties {
        if (baseUrl == null) {
            throw new IllegalArgumentException("NVIDIA_BASE_URL must be configured");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("NVIDIA_MODEL must be configured");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("NVIDIA_TIMEOUT_SECONDS must be greater than zero");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("NVIDIA_MAX_TOKENS must be greater than zero");
        }
        if (maxInputCharacters <= 0) {
            throw new IllegalArgumentException("NVIDIA_MAX_INPUT_CHARACTERS must be greater than zero");
        }
    }
}
