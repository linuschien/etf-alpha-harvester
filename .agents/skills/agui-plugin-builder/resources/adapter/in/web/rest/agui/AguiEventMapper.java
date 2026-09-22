package com.scoreassistant.adapter.in.web.rest.agui;

import com.scoreassistant.adapter.in.web.dto.agui.AguiEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

public class AguiEventMapper {

    private static final Logger log = LoggerFactory.getLogger(AguiEventMapper.class);

    private final String threadId;
    private final String runId;
    private final boolean isGemmaModel;

    private final Set<String> seenToolCalls = new HashSet<>();
    private final Set<String> completedToolCalls = new HashSet<>();
    private final Map<String, String> sentArguments = new HashMap<>();
    private final Map<Integer, String> indexToToolCallId = new HashMap<>();
    private boolean hasStartedReasoning = false;
    private boolean hasStartedText = false;
    private final String baseMessageId;
    private final String reasoningMessageId;
    private final String textMessageId;

    public AguiEventMapper(String threadId, String runId, boolean isGemmaModel) {
        this.threadId = threadId;
        this.runId = runId;
        this.isGemmaModel = isGemmaModel;
        this.baseMessageId = "msg-" + UUID.randomUUID().toString();
        this.reasoningMessageId = baseMessageId + "-reasoning";
        this.textMessageId = baseMessageId + "-text";
    }

    public Flux<ServerSentEvent<AguiEvent>> mapStream(Flux<ChatResponse> responseFlux) {
        Flux<ServerSentEvent<AguiEvent>> eventFlux = responseFlux.concatMap(this::mapChunk);

        Mono<List<ServerSentEvent<AguiEvent>>> finalEventsMono = Mono.fromCallable(this::generateEndEvents);

        return Flux.concat(
            Flux.just(AguiEvent.RunStarted.of(threadId, runId)),
            eventFlux,
            finalEventsMono.flatMapMany(Flux::fromIterable),
            Flux.just(AguiEvent.RunFinished.of(threadId, runId))
        )
        .doOnComplete(() -> log.info("AGUI Event Stream completed successfully for runId: '{}'", runId))
        .doOnCancel(() -> log.info("AGUI Event Stream was cancelled (client disconnected) for runId: '{}'", runId))
        .onErrorResume(throwable -> {
            log.warn("Exception caught in AGUI Event Stream for runId '{}': {}", runId, throwable.getMessage());
            if (throwable.getMessage() != null && 
                (throwable.getMessage().contains("Stream failed") || throwable.getMessage().contains("Connection closed"))) {
                log.info("Handling expected LM Studio stream end exception. Gracefully completing with RUN_FINISHED.");
                return Flux.just(AguiEvent.RunFinished.of(threadId, runId));
            }
            log.error("Fatal exception in AGUI Event Stream:", throwable);
            return Flux.just(AguiEvent.RunError.of("An error occurred during agent execution: " + throwable.getMessage()));
        });
    }

    private Flux<ServerSentEvent<AguiEvent>> mapChunk(ChatResponse chatResponse) {
        Generation gen = chatResponse.getResult();
        if (gen == null) {
            return Flux.empty();
        }

        List<ServerSentEvent<AguiEvent>> events = new ArrayList<>();

        AssistantMessage assistantMessage = gen.getOutput();
        if (assistantMessage != null && assistantMessage.getToolCalls() != null && !assistantMessage.getToolCalls().isEmpty()) {
            List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
            for (int i = 0; i < toolCalls.size(); i++) {
                AssistantMessage.ToolCall toolCall = toolCalls.get(i);
                String rawId = toolCall.id();
                String toolCallId = (rawId != null && !rawId.isEmpty()) ? rawId :
                        indexToToolCallId.computeIfAbsent(i, idx -> "call-idx-" + idx + "-" + UUID.randomUUID().toString());

                if (!seenToolCalls.contains(toolCallId)) {
                    seenToolCalls.add(toolCallId);
                    sentArguments.put(toolCallId, "");
                    log.info("LLM tool call start: name={}, toolCallId={}", toolCall.name(), toolCallId);
                    events.add(AguiEvent.ToolCallStart.of(toolCallId, toolCall.name()));
                }

                String fullArgs = toolCall.arguments() != null ? toolCall.arguments() : "";
                String alreadySent = sentArguments.getOrDefault(toolCallId, "");
                if (fullArgs.length() > alreadySent.length() && fullArgs.startsWith(alreadySent)) {
                    String delta = fullArgs.substring(alreadySent.length());
                    sentArguments.put(toolCallId, fullArgs);
                    log.info("[DEBUG-TOOL-ARGS] toolCall='{}' toolCallId='{}' accumulatedArgs='{}'",
                            toolCall.name(), toolCallId, fullArgs);
                    events.add(AguiEvent.ToolCallArgs.of(toolCallId, delta));
                }
            }
        }

        if (gen.getOutput() != null && gen.getOutput().getMetadata() != null) {
            log.trace("Generation metadata keys: {}", gen.getOutput().getMetadata().keySet());
            Object reasoningObj = gen.getOutput().getMetadata().get("reasoningContent");
            if (reasoningObj == null) {
                reasoningObj = gen.getOutput().getMetadata().get("reasoning_content");
            }
            if (reasoningObj instanceof String reasoningText && !reasoningText.isEmpty()) {
                if (!hasStartedReasoning) {
                    hasStartedReasoning = true;
                    log.info("LLM reasoning start: messageId={}", reasoningMessageId);
                    events.add(AguiEvent.ReasoningStart.of(reasoningMessageId));
                    events.add(AguiEvent.ReasoningMessageStart.of(reasoningMessageId));
                }
                log.debug("LLM streamed reasoning content chunk: '{}'", reasoningText);
                events.add(AguiEvent.ReasoningMessageContent.of(reasoningMessageId, reasoningText));
            }
        }

        String content = gen.getOutput() != null ? gen.getOutput().getText() : null;
        if (content != null && !content.isEmpty()) {
            if (isGemmaModel) {
                if (content.contains("<think>") || content.contains("<|channel>thought")) {
                    content = content.replace("<think>", "").replace("<|channel>thought", "");
                    hasStartedReasoning = true;
                    log.info("LLM reasoning start: messageId={}", reasoningMessageId);
                    events.add(AguiEvent.ReasoningStart.of(reasoningMessageId));
                    events.add(AguiEvent.ReasoningMessageStart.of(reasoningMessageId));
                }
                if (content.contains("</think>") || content.contains("<channel|>")) {
                    content = content.replace("</think>", "").replace("<channel|>", "");
                    hasStartedReasoning = false;
                    log.info("LLM reasoning end: messageId={}", reasoningMessageId);
                    events.add(AguiEvent.ReasoningMessageEnd.of(reasoningMessageId));
                    events.add(AguiEvent.ReasoningEnd.of(reasoningMessageId));
                }
            }

            if (hasStartedReasoning) {
                if (!content.isEmpty()) {
                    events.add(AguiEvent.ReasoningMessageContent.of(reasoningMessageId, content));
                }
            } else {
                if (!hasStartedText) {
                    hasStartedText = true;
                    log.info("LLM text start: messageId={}", textMessageId);
                    events.add(AguiEvent.TextMessageStart.of(textMessageId, "assistant"));
                }
                if (!content.isEmpty()) {
                    events.add(AguiEvent.TextMessageContent.of(textMessageId, content));
                }
            }
        }

        return Flux.fromIterable(events);
    }

    private List<ServerSentEvent<AguiEvent>> generateEndEvents() {
        List<ServerSentEvent<AguiEvent>> endEvents = new ArrayList<>();
        for (String toolCallId : seenToolCalls) {
            if (!completedToolCalls.contains(toolCallId)) {
                completedToolCalls.add(toolCallId);
                log.info("LLM tool call end: toolCallId={}", toolCallId);
                endEvents.add(AguiEvent.ToolCallEnd.of(toolCallId));
            }
        }
        if (hasStartedReasoning) {
            hasStartedReasoning = false;
            log.info("LLM reasoning end: messageId={}", reasoningMessageId);
            endEvents.add(AguiEvent.ReasoningMessageEnd.of(reasoningMessageId));
            endEvents.add(AguiEvent.ReasoningEnd.of(reasoningMessageId));
        }
        if (hasStartedText) {
            hasStartedText = false;
            log.info("LLM text end: messageId={}", textMessageId);
            endEvents.add(AguiEvent.TextMessageEnd.of(textMessageId));
        }
        return endEvents;
    }
}
