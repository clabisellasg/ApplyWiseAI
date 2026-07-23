package com.genesis.applywise.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record NvidiaChatResponse(List<Choice> choices) {
    public record Choice(
            Message message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    public record Message(String content, String refusal) {
    }
}
