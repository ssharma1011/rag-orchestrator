package com.purchasingpower.autoflow.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE event for chat streaming.
 *
 * Event types:
 * - CONNECTED: Initial connection established
 * - THINKING: Agent is processing
 * - TOOL: Agent is executing a tool
 * - PARTIAL: Partial response (streaming tokens)
 * - COMPLETE: Final response
 * - ERROR: Error occurred
 *
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatEvent {

    private String conversationId;
    private EventType type;
    private String message;
    private String content;
    private String tool;
    private Double progress;

    public enum EventType {
        CONNECTED,
        THINKING,
        TOOL,
        PARTIAL,
        COMPLETE,
        ERROR
    }

    public static ChatEvent thinking(String conversationId, String message) {
        return ChatEvent.builder()
            .conversationId(conversationId)
            .type(EventType.THINKING)
            .message(message)
            .build();
    }

    public static ChatEvent tool(String conversationId, String toolName, String status) {
        return ChatEvent.builder()
            .conversationId(conversationId)
            .type(EventType.TOOL)
            .tool(toolName)
            .message(status)
            .build();
    }

    public static ChatEvent complete(String conversationId, String response) {
        return ChatEvent.builder()
            .conversationId(conversationId)
            .type(EventType.COMPLETE)
            .content(response)
            .build();
    }

    public static ChatEvent error(String conversationId, String error) {
        return ChatEvent.builder()
            .conversationId(conversationId)
            .type(EventType.ERROR)
            .message(error)
            .build();
    }
}
