package com.scoreassistant.adapter.in.web.dto.agui;

/**
 * DTO representing a frontend-registered tool (CopilotKit v2 {@code FrontendTool}).
 * Corresponds to items in the {@code tools} array of the AGUI v2 request payload.
 */
public record FrontendToolDto(
    String name,
    String description,
    Object parameters
) {}
