package com.scoreassistant.adapter.in.web.dto.agui;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request payload sent by the AGUI v2 client.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AguiChatRequest(
    List<ChatMessageDto> messages,
    List<ContextDto> context,
    List<FrontendToolDto> tools,
    String threadId,
    String runId
) {
    public AguiChatRequest(List<ChatMessageDto> messages, List<ContextDto> context, List<FrontendToolDto> tools) {
        this(messages, context, tools, null, null);
    }
}
