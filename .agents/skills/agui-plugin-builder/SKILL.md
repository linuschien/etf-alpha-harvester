---
name: agui-plugin-builder
description: Standard skill for implementing Agentic AI Plugins using the AGUI framework. Provides the 'One-In, One-Out' Hexagonal architecture using AbstractAguiAgent base class and LlmPort.
---

# AGUI Plugin Builder

This skill dictates how to build Agentic AI plugins using the AGUI Framework. The framework enables "One-In, One-Out" Hexagonal Architecture, bridging CopilotKit V2+ (Frontend) and Spring AI 2.0.0+ (Backend).

## Core Concepts

1. **Agent as a Plugin**: Each Agentic AI is an independent plugin extending `AbstractAguiAgent`.
2. **One-In, One-Out**:
   - `Adapter In`: `GenericAguiRuntimeController` acts as a universal SSE chat endpoint (`/api/agui/{agentId}/chat`). It dynamically routes traffic to your agent bean.
   - `Application Core`: Your custom agent (subclassing `AbstractAguiAgent`) handles Prompt building, Tool orchestration, and System Instructions.
   - `Adapter Out`: `SpringAiLlmAdapter` implements `LlmPort` and encapsulates the actual LLM `ChatModel` interaction.

## Building a New Agent

To build a new Agentic AI plugin:

### 1. Create the Agent Class
Extend `AbstractAguiAgent`. Define your system instruction, welcome message, and register tools.

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoreassistant.application.agent.AbstractAguiAgent;
import com.scoreassistant.adapter.in.web.dto.agui.AguiChatRequest;
import com.scoreassistant.domain.ports.output.LlmPort;

@Component
public class MyCustomAgent extends AbstractAguiAgent {

    @Autowired
    public MyCustomAgent(LlmPort llmPort, @Autowired(required = false) ObjectMapper objectMapper) {
        super(llmPort, objectMapper);
    }

    @Override
    public String getId() {
        return "my-custom-agent";
    }

    @Override
    public String getWelcomeMessage() {
        return "Hello! I am your custom agent.";
    }

    @Override
    protected String getSystemInstruction(AguiChatRequest request) {
        return "You are a helpful AI assistant.";
    }
}
```

### 2. Add Tools
- **Backend Tools**: Define Spring beans implementing `java.util.function.Function` with `@Description` or `@Tool` annotations. Pass them by overriding `getTools()` to return a `List.of("myToolFunctionName")`.
- **Frontend Tools**: These are passed dynamically from the frontend via `AguiChatRequest.tools`. `AbstractAguiAgent` automatically converts them into `FrontendToolCallback` instances and registers them with the LLM prompt.

## Bootstrapping a New Project

If you are setting up AGUI in a brand new project, copy the reference files provided in this skill's `resources/` directory into the new project's source tree. Do not rewrite these components manually.
