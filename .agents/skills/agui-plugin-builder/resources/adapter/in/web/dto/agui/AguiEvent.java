package com.scoreassistant.adapter.in.web.dto.agui;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.codec.ServerSentEvent;

/**
 * Interface representing a strongly-typed AGUI Server-Sent Event payload.
 */
public sealed interface AguiEvent permits
        AguiEvent.RunStarted,
        AguiEvent.TextMessageStart,
        AguiEvent.TextMessageContent,
        AguiEvent.TextMessageEnd,
        AguiEvent.ToolCallStart,
        AguiEvent.ToolCallArgs,
        AguiEvent.ToolCallEnd,
        AguiEvent.RunFinished,
        AguiEvent.RunError,
        AguiEvent.ReasoningStart,
        AguiEvent.ReasoningMessageStart,
        AguiEvent.ReasoningMessageContent,
        AguiEvent.ReasoningMessageEnd,
        AguiEvent.ReasoningEnd {

    @JsonProperty("type")
    String type();

    record RunStarted(
            String type,
            String threadId,
            String runId
    ) implements AguiEvent {
        public static ServerSentEvent<AguiEvent> of(String threadId, String runId) {
            return ServerSentEvent.<AguiEvent>builder(new RunStarted("RUN_STARTED", threadId, runId)).build();
        }
    }

    record TextMessageStart(
            @JsonProperty("type") String type,
            @JsonProperty("messageId") String messageId,
            @JsonProperty("role") String role
    ) implements AguiEvent {
        public static ServerSentEvent<AguiEvent> of(String messageId, String role) {
            return ServerSentEvent.<AguiEvent>builder(new TextMessageStart("TEXT_MESSAGE_START", messageId, role)).build();
        }
    }

    record TextMessageContent(
            String type,
            String messageId,
            String delta
    ) implements AguiEvent {
        public static ServerSentEvent<AguiEvent> of(String messageId, String delta) {
            return ServerSentEvent.<AguiEvent>builder(new TextMessageContent("TEXT_MESSAGE_CONTENT", messageId, delta)).build();
        }
    }

    record TextMessageEnd(
            @JsonProperty("type") String type,
            @JsonProperty("messageId") String messageId
    ) implements AguiEvent {
        public static ServerSentEvent<AguiEvent> of(String messageId) {
            return ServerSentEvent.<AguiEvent>builder(new TextMessageEnd("TEXT_MESSAGE_END", messageId)).build();
        }
    }

    record ReasoningStart(
            @JsonProperty("type") String type,
            @JsonProperty("messageId") String messageId
    ) implements AguiEvent {
        public static ServerSentEvent<AguiEvent> of(String messageId) {
            return ServerSentEvent.<AguiEvent>builder(new ReasoningStart("REASONING_START", messageId)).build();
        }
    }

    record ReasoningMessageStart(
            @JsonProperty("type") String type,
            @JsonProperty("messageId") String messageId,
            @JsonProperty("role") String role
    ) implements AguiEvent {
        public static ServerSentEvent<AguiEvent> of(String messageId) {
            return ServerSentEvent.<AguiEvent>builder(new ReasoningMessageStart("REASONING_MESSAGE_START", messageId, "reasoning")).build();
        }
    }

    record ReasoningMessageContent(
            @JsonProperty("type") String type,
            @JsonProperty("messageId") String messageId,
            @JsonProperty("delta") String delta
    ) implements AguiEvent {
        public static ServerSentEvent<AguiEvent> of(String messageId, String delta) {
            return ServerSentEvent.<AguiEvent>builder(new ReasoningMessageContent("REASONING_MESSAGE_CONTENT", messageId, delta)).build();
        }
    }

    record ReasoningMessageEnd(
            @JsonProperty("type") String type,
            @JsonProperty("messageId") String messageId
    ) implements AguiEvent {
        public static ServerSentEvent<AguiEvent> of(String messageId) {
            return ServerSentEvent.<AguiEvent>builder(new ReasoningMessageEnd("REASONING_MESSAGE_END", messageId)).build();
        }
    }

    record ReasoningEnd(
            @JsonProperty("type") String type,
            @JsonProperty("messageId") String messageId
    ) implements AguiEvent {
        public static ServerSentEvent<AguiEvent> of(String messageId) {
            return ServerSentEvent.<AguiEvent>builder(new ReasoningEnd("REASONING_END", messageId)).build();
        }
    }

    record ToolCallStart(
            String type,
            String toolCallId,
            String toolCallName
    ) implements AguiEvent {
        public static ServerSentEvent<AguiEvent> of(String toolCallId, String toolCallName) {
            return ServerSentEvent.<AguiEvent>builder(new ToolCallStart("TOOL_CALL_START", toolCallId, toolCallName)).build();
        }
    }

    record ToolCallArgs(
            String type,
            String toolCallId,
            String delta
    ) implements AguiEvent {
        public static ServerSentEvent<AguiEvent> of(String toolCallId, String delta) {
            return ServerSentEvent.<AguiEvent>builder(new ToolCallArgs("TOOL_CALL_ARGS", toolCallId, delta)).build();
        }
    }

    record ToolCallEnd(
            String type,
            String toolCallId
    ) implements AguiEvent {
        public static ServerSentEvent<AguiEvent> of(String toolCallId) {
            return ServerSentEvent.<AguiEvent>builder(new ToolCallEnd("TOOL_CALL_END", toolCallId)).build();
        }
    }

    record RunFinished(
            String type,
            String threadId,
            String runId,
            Outcome outcome
    ) implements AguiEvent {
        public record Outcome(String type) {}
        
        public static ServerSentEvent<AguiEvent> of(String threadId, String runId) {
            return ServerSentEvent.<AguiEvent>builder(new RunFinished("RUN_FINISHED", threadId, runId, new Outcome("success"))).build();
        }
    }

    record RunError(
            String type,
            String message
    ) implements AguiEvent {
        public static ServerSentEvent<AguiEvent> of(String message) {
            return ServerSentEvent.<AguiEvent>builder(new RunError("RUN_ERROR", message)).build();
        }
    }
}
