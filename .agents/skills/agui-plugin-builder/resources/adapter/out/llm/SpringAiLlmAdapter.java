package com.scoreassistant.adapter.out.llm;

import com.scoreassistant.domain.ports.output.LlmPort;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class SpringAiLlmAdapter implements LlmPort {

    private final ChatModel chatModel;

    @Value("${spring.ai.openai.chat.options.model:}")
    private String modelName;

    public SpringAiLlmAdapter(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return chatModel.stream(prompt);
    }

    @Override
    public boolean isGemmaModel() {
        return modelName != null && modelName.toLowerCase().contains("gemma");
    }

    @Override
    public org.springframework.ai.chat.prompt.ChatOptions getDefaultOptions() {
        return chatModel.getDefaultOptions();
    }
}
