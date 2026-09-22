package com.scoreassistant.adapter.in.web.rest.agui;

import com.scoreassistant.adapter.in.web.dto.agui.*;
import com.scoreassistant.application.agent.AguiAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller exposing the generic AGUI protocol runtime endpoint.
 * Dispatches agent executions dynamically by agentId and handles SSE streaming.
 */
@RestController
@RequestMapping("/api/agui")
public class GenericAguiRuntimeController {

    private static final Logger log = LoggerFactory.getLogger(GenericAguiRuntimeController.class);

    private final Map<String, AguiAgent> agentRegistry = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public GenericAguiRuntimeController(List<AguiAgent> agents, @Autowired(required = false) ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper.copy() : new ObjectMapper();
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        for (AguiAgent agent : agents) {
            this.agentRegistry.put(agent.getId(), agent);
        }
    }

    @RequestMapping(value = "/{agentId}/chat/info", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Object> handleHandshakeInfo(
            @PathVariable("agentId") String agentId) {
        AguiAgent agent = agentRegistry.get(agentId);
        if (agent == null) {
            log.error("Handshake failed: Agent '{}' not found", agentId);
            return ResponseEntity.status(444).body(Map.of(
                "type", "RUN_ERROR",
                "message", "Agent not found: " + agentId
            ));
        }

        log.info("Received AGUI handshake 'info' request for agentId: '{}'", agentId);
        Map<String, Object> infoResponse = Map.of(
            "version", "1.0",
            "mode", "sse",
            "agents", Map.of(
                "default", Map.of(
                    "description", agent.getId() + " assistant",
                    "capabilities", Map.of()
                ),
                agentId, Map.of(
                    "description", agent.getId() + " assistant",
                    "capabilities", Map.of()
                )
            )
        );
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(infoResponse);
    }

    @PostMapping(value = "/{agentId}/chat")
    public ResponseEntity<Object> handleAgentChat(
            @PathVariable("agentId") String agentId,
            @RequestBody String requestBodyJson) {

        JsonNode root;
        try {
            root = objectMapper.readTree(requestBodyJson);
        } catch (Exception e) {
            log.error("Failed to parse request JSON payload: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "type", "RUN_ERROR",
                "message", "Failed to parse request payload: " + e.getMessage()
            ));
        }

        if (root.has("method") && "info".equals(root.get("method").asText())) {
            return handleHandshakeInfo(agentId);
        } else {
            return handleAgentChatExecution(agentId, root);
        }
    }

    private ResponseEntity<Object> handleAgentChatExecution(String agentId, JsonNode root) {
        AguiAgent agent = agentRegistry.get(agentId);
        if (agent == null) {
            log.error("Agent execution failed: Agent '{}' not found", agentId);
            return errorResponse(444, "Agent not found: " + agentId);
        }

        AguiChatRequest request;
        try {
            request = parseChatRequest(root);
        } catch (Exception e) {
            log.error("Failed to map request body to DTO: {}", e.getMessage());
            return errorResponse(400, "Failed to deserialize request: " + e.getMessage());
        }

        log.info("Received AGUI chat request for agentId: '{}', threadId: '{}', runId: '{}'", 
                agentId, request.threadId(), request.runId());
        log.info("Raw request body: {}", root.toString());
        log.info("Received payload counts - Messages: {}, Context variables: {}, Registered tools: {}",
                request.messages() != null ? request.messages().size() : 0,
                request.context() != null ? request.context().size() : 0,
                request.tools() != null ? request.tools().size() : 0);

        if (isWelcomeRequest(request)) {
            return welcomeResponse(agent, request);
        }

        try {
            String finalThreadId = request.threadId() != null ? request.threadId() : UUID.randomUUID().toString();
            String finalRunId = request.runId() != null ? request.runId() : UUID.randomUUID().toString();

            // Delegate core execution to the Application Agent Service (One-In-One-Out)
            Flux<ChatResponse> rawResponseStream = agent.execute(request);
            
            // Map the domain stream to protocol-specific AGUI SSE stream
            AguiEventMapper mapper = new AguiEventMapper(finalThreadId, finalRunId, agent.isGemmaModel());
            Flux<ServerSentEvent<AguiEvent>> sseStream = mapper.mapStream(rawResponseStream);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(sseStream);
        } catch (Exception e) {
            log.error("Fatal exception during agent chat setup: ", e);
            return errorResponse(500, "Failed to start agent session: " + e.getMessage());
        }
    }

    private AguiChatRequest parseChatRequest(JsonNode root) throws Exception {
        if (root.has("body") && (root.has("method") || root.has("params"))) {
            return objectMapper.treeToValue(root.get("body"), AguiChatRequest.class);
        } else {
            return objectMapper.treeToValue(root, AguiChatRequest.class);
        }
    }

    private boolean isWelcomeRequest(AguiChatRequest request) {
        return request.messages() == null || request.messages().isEmpty();
    }

    private ResponseEntity<Object> welcomeResponse(AguiAgent agent, AguiChatRequest request) {
        String finalThreadId = request.threadId() != null ? request.threadId() : UUID.randomUUID().toString();
        String finalRunId = request.runId() != null ? request.runId() : UUID.randomUUID().toString();
        String messageId = "msg-" + UUID.randomUUID().toString();

        log.info("Empty messages payload. Returning instant welcome response for agentId: '{}'", agent.getId());
        Flux<ServerSentEvent<AguiEvent>> welcomeFlux = Flux.just(
            AguiEvent.RunStarted.of(finalThreadId, finalRunId),
            AguiEvent.TextMessageStart.of(messageId, "assistant"),
            AguiEvent.TextMessageContent.of(messageId, agent.getWelcomeMessage()),
            AguiEvent.TextMessageEnd.of(messageId),
            AguiEvent.RunFinished.of(finalThreadId, finalRunId)
        );
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(welcomeFlux);
    }

    private ResponseEntity<Object> errorResponse(int status, String message) {
        return ResponseEntity.status(status).body(Map.of(
            "type", "RUN_ERROR",
            "message", message
        ));
    }

    @GetMapping(value = "/{agentId}/chat/threads")
    public ResponseEntity<Object> handleGetThreads(
            @PathVariable("agentId") String agentId,
            @RequestParam(value = "agentId", required = false) String queryAgentId) {
        log.info("Received GET threads request for agentId: '{}', queryAgentId: '{}'", agentId, queryAgentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("threads", List.of()));
    }

    @PostMapping(value = "/{agentId}/chat/threads")
    public ResponseEntity<Object> handleCreateThread(
            @PathVariable("agentId") String agentId) {
        String threadId = UUID.randomUUID().toString();
        log.info("Received POST create thread request for agentId: '{}'. Generated threadId: '{}'", agentId, threadId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("threadId", threadId));
    }

    @RequestMapping(value = "/{agentId}/chat/threads/{threadId}/**", method = {RequestMethod.POST, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<Object> handleThreadMutations(
            @PathVariable("agentId") String agentId,
            @PathVariable("threadId") String threadId) {
        log.info("Received thread mutation request for agentId: '{}', threadId: '{}'", agentId, threadId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("ok", true));
    }
}
