package com.purchasingpower.autoflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.api.ChatEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing SSE streams for chat conversations.
 * Optimized for Tool Orchestration:
 * - Deterministic cleanup on client disconnect
 * - Silent handling of ClientAbortException
 * - Thread-safe event buffering
 * - FIXED: Graceful error handling using SSE events instead of completeWithError to avoid 406/500 mapping errors.
 * - Restored sendError method for ChatController compatibility.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStreamService {

    private final ObjectMapper objectMapper;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, List<ChatEvent>> eventBuffer = new ConcurrentHashMap<>();

    private static final long SSE_TIMEOUT_MS = 15 * 60 * 1000; // 15 minutes
    private static final int MAX_BUFFERED_EVENTS = 100;

    public SseEmitter createStream(String conversationId) {
        log.info("Creating SSE stream for chat: {}", conversationId);

        if (conversationId == null || conversationId.isBlank() || "null".equals(conversationId)) {
            log.warn("Rejected SSE connection with invalid conversationId");
            return createErrorEmitter("Invalid conversationId.");
        }

        // Cleanup any existing stale emitter for this conversation
        removeEmitter(conversationId);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onCompletion(() -> cleanup(conversationId, "completed"));
        emitter.onTimeout(() -> cleanup(conversationId, "timed out"));
        emitter.onError((ex) -> cleanup(conversationId, "error: " + ex.getMessage()));

        emitters.put(conversationId, emitter);

        try {
            // Send initial connection event
            ChatEvent connectEvent = ChatEvent.builder()
                    .conversationId(conversationId)
                    .type(ChatEvent.EventType.CONNECTED)
                    .message("Connected to chat stream")
                    .build();

            sendEventInternal(conversationId, emitter, connectEvent);

            // Replay buffered events
            List<ChatEvent> buffered = eventBuffer.remove(conversationId);
            if (buffered != null && !buffered.isEmpty()) {
                log.debug("Replaying {} buffered events for: {}", buffered.size(), conversationId);
                for (ChatEvent event : buffered) {
                    sendEventInternal(conversationId, emitter, event);
                }
            }
        } catch (Exception e) {
            log.error("Failed to initialize SSE stream for {}", conversationId, e);
            cleanup(conversationId, "init failure");
        }

        return emitter;
    }

    /**
     * Send "thinking" event - agent is processing.
     */
    public void sendThinking(String conversationId, String message) {
        sendUpdate(conversationId, ChatEvent.builder()
                .conversationId(conversationId)
                .type(ChatEvent.EventType.THINKING)
                .message(message)
                .build());
    }

    /**
     * Send "tool" event - agent is executing a tool.
     */
    public void sendToolExecution(String conversationId, String toolName, String status) {
        sendUpdate(conversationId, ChatEvent.builder()
                .conversationId(conversationId)
                .type(ChatEvent.EventType.TOOL)
                .tool(toolName)
                .message(status)
                .build());
    }

    /**
     * Send partial response (for streaming tokens).
     */
    public void sendPartialResponse(String conversationId, String content) {
        sendUpdate(conversationId, ChatEvent.builder()
                .conversationId(conversationId)
                .type(ChatEvent.EventType.PARTIAL)
                .content(content)
                .build());
    }

    /**
     * Send complete response and close stream.
     */
    public void sendComplete(String conversationId, String response) {
        sendUpdate(conversationId, ChatEvent.builder()
                .conversationId(conversationId)
                .type(ChatEvent.EventType.COMPLETE)
                .content(response)
                .message("Response complete")
                .build());

        cleanup(conversationId, "explicit completion");
    }

    /**
     * FIXED: Send error and close stream gracefully.
     * Re-added to fix ChatController compatibility.
     * Uses explicit sendUpdate + complete() instead of completeWithError to avoid SSE converter crashes.
     */
    public void sendError(String conversationId, String error) {
        log.error("Sending error event for conversation {}: {}", conversationId, error);
        ChatEvent event = ChatEvent.builder()
                .conversationId(conversationId)
                .type(ChatEvent.EventType.ERROR)
                .message(error)
                .build();

        // 1. Send the actual error event via JSON event stream
        sendUpdate(conversationId, event);

        // 2. Perform a standard closure of the emitter
        SseEmitter emitter = emitters.get(conversationId);
        if (emitter != null) {
            cleanup(conversationId, "explicit error sent");
        }
    }

    public void sendUpdate(String conversationId, ChatEvent event) {
        SseEmitter emitter = emitters.get(conversationId);

        if (emitter == null) {
            List<ChatEvent> buffer = eventBuffer.computeIfAbsent(conversationId, k -> new ArrayList<>());
            if (buffer.size() < MAX_BUFFERED_EVENTS) {
                buffer.add(event);
                log.debug("Buffered chat event: {} type={}", conversationId, event.getType());
            }
            return;
        }

        sendEventInternal(conversationId, emitter, event);
    }

    private void sendEventInternal(String conversationId, SseEmitter emitter, ChatEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event()
                    .id(UUID.randomUUID().toString())
                    .name("chat-update")
                    .data(json));
        } catch (IOException | IllegalStateException e) {
            log.warn("SSE stream for {} aborted by client ({}). Cleaning up.", conversationId, e.getMessage());
            cleanup(conversationId, "abort");
        }
    }

    private void cleanup(String conversationId, String reason) {
        SseEmitter emitter = emitters.remove(conversationId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception ignored) {}
        }

        // Only clear buffer on successful completion or terminal error, keep on client abort
        if (!"abort".equals(reason)) {
            eventBuffer.remove(conversationId);
        }
        log.debug("SSE Resource cleanup for {}: {}", conversationId, reason);
    }

    private void removeEmitter(String conversationId) {
        SseEmitter old = emitters.remove(conversationId);
        if (old != null) {
            try { old.complete(); } catch (Exception ignored) {}
        }
    }

    private SseEmitter createErrorEmitter(String message) {
        SseEmitter errorEmitter = new SseEmitter(5000L);
        try {
            ChatEvent errorEvent = ChatEvent.builder()
                    .type(ChatEvent.EventType.ERROR)
                    .message(message)
                    .build();
            errorEmitter.send(SseEmitter.event()
                    .name("chat-update")
                    .data(objectMapper.writeValueAsString(errorEvent)));
            errorEmitter.complete();
        } catch (Exception ignored) {}
        return errorEmitter;
    }

    public boolean hasActiveStream(String conversationId) {
        return emitters.containsKey(conversationId);
    }
}