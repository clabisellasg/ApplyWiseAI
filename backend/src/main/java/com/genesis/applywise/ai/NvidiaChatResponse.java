package com.genesis.applywise.ai;

import java.util.List;

public record NvidiaChatResponse(List<Choice> choices) {
    public record Choice(Message message) {
    }

    public record Message(String content) {
    }
}
