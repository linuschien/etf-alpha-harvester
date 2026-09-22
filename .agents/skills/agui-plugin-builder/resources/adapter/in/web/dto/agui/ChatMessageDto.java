package com.scoreassistant.adapter.in.web.dto.agui;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessageDto {
    
    @JsonProperty("role")
    private String role;
    
    @JsonProperty("content")
    private JsonNode content;
    
    @JsonProperty("tool_calls")
    @JsonAlias({"tool_calls", "toolCalls"})
    private List<ToolCallDto> toolCalls;
    
    @JsonProperty("tool_call_id")
    @JsonAlias({"tool_call_id", "toolCallId"})
    private String toolCallId;
    
    @JsonProperty("name")
    private String name;

    public ChatMessageDto() {}

    public ChatMessageDto(String role, JsonNode content) {
        this.role = role;
        this.content = content;
    }

    public ChatMessageDto(String role, JsonNode content, List<ToolCallDto> toolCalls, String toolCallId, String name) {
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
        this.name = name;
    }

    @JsonProperty("role")
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    @JsonProperty("content")
    public JsonNode getContent() { return content; }
    public void setContent(JsonNode content) { this.content = content; }

    @JsonProperty("tool_calls")
    public List<ToolCallDto> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCallDto> toolCalls) { this.toolCalls = toolCalls; }

    @JsonProperty("tool_call_id")
    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    @JsonProperty("name")
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolCallDto {
        @JsonProperty("id")
        private String id;
        
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("function")
        private FunctionDto function;

        public ToolCallDto() {}

        public ToolCallDto(String id, String type, FunctionDto function) {
            this.id = id;
            this.type = type;
            this.function = function;
        }

        @JsonProperty("id")
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        @JsonProperty("type")
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        @JsonProperty("function")
        public FunctionDto getFunction() { return function; }
        public void setFunction(FunctionDto function) { this.function = function; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionDto {
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("arguments")
        private String arguments;

        public FunctionDto() {}

        public FunctionDto(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        @JsonProperty("name")
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        @JsonProperty("arguments")
        public String getArguments() { return arguments; }
        public void setArguments(String arguments) { this.arguments = arguments; }
    }
}
