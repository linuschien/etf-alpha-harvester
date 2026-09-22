package com.scoreassistant.domain.ports.output;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.prompt.ChatOptions;

public interface LlmPort {
    Flux<ChatResponse> stream(Prompt prompt);
    boolean isGemmaModel();
    ChatOptions getDefaultOptions();
}
