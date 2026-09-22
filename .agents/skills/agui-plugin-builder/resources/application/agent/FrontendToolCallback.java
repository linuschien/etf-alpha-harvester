package com.scoreassistant.application.agent;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Custom ToolCallback implementation to represent frontend actions dynamically
 * in the Spring AI chat client request. Since the handler for these actions
 * resides on the client side (in the browser), the call method simply returns
 * a placeholder response and relies on the controller yielding a tool_call event.
 */
public class FrontendToolCallback implements ToolCallback {

    private final ToolDefinition definition;

    public FrontendToolCallback(String name, String description, String inputSchemaJson) {
        this.definition = ToolDefinition.builder()
                .name(name)
                .description(description)
                .inputSchema(inputSchemaJson)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return this.definition;
    }

    @Override
    public String call(String toolInput) {
        // This is a stub backend implementation. When the LLM decides to invoke this tool,
        // the AbstractAguiAgent will intercept it from the stream and forward it
        // as a "tool_call" Server-Sent Event (SSE) to the frontend.
        return "frontend_deferred";
    }
}
