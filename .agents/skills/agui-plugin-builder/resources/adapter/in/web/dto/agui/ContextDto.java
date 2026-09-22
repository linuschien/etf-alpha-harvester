package com.scoreassistant.adapter.in.web.dto.agui;

/**
 * DTO representing a frontend context variable (CopilotKit v2 {@code AgentContextInput}).
 * Corresponds to items in the {@code context} array of the AGUI v2 request payload.
 */
public record ContextDto(
    String description,
    String value
) {}
