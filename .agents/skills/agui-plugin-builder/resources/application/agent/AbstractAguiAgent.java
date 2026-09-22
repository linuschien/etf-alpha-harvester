package com.scoreassistant.application.agent;

import com.scoreassistant.adapter.in.web.dto.agui.*;
import com.scoreassistant.domain.ports.output.LlmPort;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public abstract class AbstractAguiAgent implements AguiAgent {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final LlmPort llmPort;
    protected final ObjectMapper objectMapper;

    public AbstractAguiAgent(LlmPort llmPort, ObjectMapper objectMapper) {
        this.llmPort = llmPort;
        this.objectMapper = objectMapper != null ? objectMapper.copy() : new ObjectMapper();
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public boolean isGemmaModel() {
        return llmPort.isGemmaModel();
    }

    @Override
    public Flux<ChatResponse> execute(AguiChatRequest request) {
        Prompt prompt = buildPrompt(request);
        return llmPort.stream(prompt);
    }

    protected abstract String getSystemInstruction(AguiChatRequest request);
    protected abstract List<Object> getTools();
    protected abstract ChatOptions getChatOptions(List<ToolCallback> dynamicTools);

    private Prompt buildPrompt(AguiChatRequest request) {
        StringBuilder systemPrompt = new StringBuilder(getSystemInstruction(request));
        if (request.context() != null && !request.context().isEmpty()) {
            systemPrompt.append("\n\n當前網頁狀態資料 (Current Frontend Readables Context):\n");
            for (ContextDto readable : request.context()) {
                systemPrompt.append(String.format("- %s: %s\n", readable.description(), readable.value()));
                log.debug("Bound frontend context readable: '{}' = '{}'", readable.description(), readable.value());
            }
        }

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt.toString()));
        
        Map<String, String> toolCallIdToName = new HashMap<>();

        if (request.messages() != null) {
            int lastIndex = request.messages().size() - 1;
            for (int i = 0; i < request.messages().size(); i++) {
                ChatMessageDto msg = request.messages().get(i);
                Message mapped = null;
                boolean isLast = (i == lastIndex);
                
                if ("user".equals(msg.getRole())) {
                    mapped = mapUserMessage(msg, isLast, isGemmaModel());
                } else if ("assistant".equals(msg.getRole())) {
                    mapped = mapAssistantMessage(msg, toolCallIdToName);
                } else if ("tool".equals(msg.getRole())) {
                    mapped = mapToolMessage(msg, toolCallIdToName);
                } else {
                    log.debug("Skipping unknown role '{}' message", msg.getRole());
                }
                if (mapped != null) {
                    messages.add(mapped);
                }
            }
        }

        List<ToolCallback> toolsList = new ArrayList<>();
        if (getTools() != null) {
            for (Object tool : getTools()) {
                if (tool instanceof ToolCallback) {
                    toolsList.add((ToolCallback) tool);
                }
            }
        }

        if (request.tools() != null && !request.tools().isEmpty()) {
            for (FrontendToolDto action : request.tools()) {
                String inputSchemaJson = "{}";
                try {
                    if (action.parameters() != null) {
                        inputSchemaJson = objectMapper.writeValueAsString(action.parameters());
                    }
                } catch (Exception e) {
                    log.warn("Failed to serialize parameters for frontend tool '{}'", action.name(), e);
                }
                toolsList.add(new FrontendToolCallback(action.name(), action.description(), inputSchemaJson));
                log.info("Registered frontend tool: '{}' (params: {})", action.name(), inputSchemaJson);
            }
        }

        ChatOptions options = getChatOptions(toolsList);
        return new Prompt(messages, options);
    }

    private Message mapUserMessage(ChatMessageDto msg, boolean isLast, boolean isGemma) {
        StringBuilder textContent = new StringBuilder();
        List<Media> mediaList = new ArrayList<>();

        if (msg.getContent() != null) {
            if (msg.getContent().isTextual()) {
                textContent.append(msg.getContent().asText());
            } else if (msg.getContent().isArray()) {
                for (JsonNode part : msg.getContent()) {
                    if (part.has("type")) {
                        String type = part.get("type").asText();
                        if ("text".equals(type) && part.has("text")) {
                            textContent.append(part.get("text").asText());
                        } else if ("image".equals(type) && part.has("source")) {
                            JsonNode source = part.get("source");
                            if (source.has("type") && "data".equals(source.get("type").asText())) {
                                String base64 = source.get("value").asText();
                                String mimeTypeStr = source.has("mimeType") ? source.get("mimeType").asText() : "image/jpeg";
                                MimeType mimeType = MimeTypeUtils.parseMimeType(mimeTypeStr);
                                byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64);
                                ByteArrayResource resource = new ByteArrayResource(decodedBytes);
                                mediaList.add(new Media(mimeType, resource));
                            }
                        }
                    }
                }
            }
        }

        String finalContent = textContent.toString();
        if (isLast && isGemma) {
            finalContent = "<|think|>\n" + finalContent;
            log.debug("Injected <|think|> token into the LAST user message: '{}'", finalContent);
        } else {
            log.debug("Added User message to prompt context: '{}'", finalContent);
        }

        if (!mediaList.isEmpty()) {
            return UserMessage.builder()
                .text(finalContent)
                .media(mediaList.toArray(new Media[0]))
                .build();
        }
        return UserMessage.builder().text(finalContent).build();
    }

    private Message mapAssistantMessage(ChatMessageDto msg, Map<String, String> toolCallIdToName) {
        String assistantContent = msg.getContent() != null && msg.getContent().isTextual() ? msg.getContent().asText() : "";
        if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
            List<AssistantMessage.ToolCall> springToolCalls = new ArrayList<>();
            for (ChatMessageDto.ToolCallDto tc : msg.getToolCalls()) {
                String tcType = tc.getType() != null ? tc.getType() : "function";
                String tcName = tc.getFunction() != null ? tc.getFunction().getName() : "";
                String tcArgs = tc.getFunction() != null ? tc.getFunction().getArguments() : "{}";
                springToolCalls.add(new AssistantMessage.ToolCall(tc.getId(), tcType, tcName, tcArgs));
                toolCallIdToName.put(tc.getId(), tcName);
                log.debug("Parsed tool call in assistant message: id={}, name={}", tc.getId(), tcName);
            }
            
            log.debug("Added Assistant message with {} tool calls: '{}'", springToolCalls.size(), assistantContent);
            return AssistantMessage.builder()
                    .content(assistantContent)
                    .toolCalls(springToolCalls)
                    .build();
        } else {
            if (assistantContent.isBlank()) {
                log.debug("Skipping blank assistant message (no text, no tool calls)");
                return null;
            }
            log.debug("Added Assistant message to prompt context: '{}'", assistantContent);
            return new AssistantMessage(assistantContent);
        }
    }

    private Message mapToolMessage(ChatMessageDto msg, Map<String, String> toolCallIdToName) {
        String toolCallId = msg.getToolCallId();
        if (toolCallId == null) {
            log.warn("Skipping 'tool' message because tool_call_id is null: content='{}'", msg.getContent());
            return null;
        }
        
        String toolName = "";
        if (msg.getName() != null) {
            toolName = msg.getName();
            toolCallIdToName.put(toolCallId, toolName);
        } else {
            toolName = toolCallIdToName.getOrDefault(toolCallId, "");
        }
        String responseContent = msg.getContent() != null && msg.getContent().isTextual() ? msg.getContent().asText() : "";
        
        ToolResponseMessage.ToolResponse toolResponse = new ToolResponseMessage.ToolResponse(toolCallId, toolName, responseContent);
        log.debug("Added ToolResponseMessage: id={}, name={}, content='{}'", toolCallId, toolName, responseContent);
        return ToolResponseMessage.builder()
                .responses(List.of(toolResponse))
                .build();
    }
}
