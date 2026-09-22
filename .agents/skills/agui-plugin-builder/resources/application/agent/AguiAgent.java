package com.scoreassistant.application.agent;

import com.scoreassistant.adapter.in.web.dto.agui.AguiChatRequest;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * Interface representing a task-specific AI Agent compatible with the AGUI runtime.
 */
public interface AguiAgent {

    /**
     * Unique identifier for the Agent (e.g. "attendance-agent").
     */
    String getId();

    /**
     * Gets the welcome message to return when the conversation history is empty.
     */
    default String getWelcomeMessage() {
        return "您好！我是您的 AI 助教。請問有什麼我可以協助您的？";
    }

    /**
     * Executes the agent interaction using the incoming request, yielding a stream of raw chat responses.
     */
    Flux<ChatResponse> execute(AguiChatRequest request);

    /**
     * Returns whether the underlying LLM port is a Gemma model (which needs special handling).
     */
    boolean isGemmaModel();
}
