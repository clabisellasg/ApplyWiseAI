package com.genesis.applywise.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record NvidiaChatRequest(
        String model,
        List<Message> messages,
        boolean stream,
        double temperature,
        @JsonProperty("top_p") double topP,
        @JsonProperty("max_tokens") int maxTokens,
        @JsonProperty("chat_template_kwargs") ChatTemplateKwargs chatTemplateKwargs,
        NvidiaExtension nvext
) {
    public record Message(String role, String content) {
    }

    public record ChatTemplateKwargs(
            @JsonProperty("enable_thinking") boolean enableThinking
    ) {
    }

    public record NvidiaExtension(
            @JsonProperty("guided_json") JsonNode guidedJson
    ) {
    }
}
