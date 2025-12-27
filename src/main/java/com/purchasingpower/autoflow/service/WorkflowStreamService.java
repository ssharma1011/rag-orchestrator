package com.purchasingpower.autoflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.model.dto.WorkflowEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing Server-Sent Event (SSE) streams for workflow updates.
 *
 * Replaces polling with real-time push notifications to frontend clients.
 *
 * Usage:
 * 1. Frontend calls GET /api/v1/workflows/{conversationId}/stream
 * 2. WorkflowStreamService creates an SseEmitter
 * 3. AutoFlowWorkflow calls sendUpdate() as agents execute
 * 4. Frontend receives real-time updates via EventSource
 *
 * CRITICAL: Buffers early events to handle race condition where workflow
 * starts before frontend connects to SSE stream.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowStreamService {

    private final ObjectMapper objectMapper;

    /**
     * Map of conversationId -> SseEmitter for active streams.
     */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Buffer for events sent before client connects.
     * Key: conversationId, Value: List of buffered events
     */
    private final Map<String, List<WorkflowEvent>> eventBuffer = new ConcurrentHashMap<>();

    /**
     * Timeout for SSE connections (5 minutes).
     */
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000;

    /**
     * Max buffered events per conversation (prevent memory leak).
     */
    private static final int MAX_BUFFERED_EVENTS = 100;

    /**
     * Create a new SSE stream for a conversation.
     *
     * @param conversationId Conversation ID
     * @return SseEmitter for this conversation
     */
    public SseEmitter createStream(String conversationId) {
        log.info("📡 Creating SSE stream for conversation: {}", conversationId);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // Register callbacks
        emitter.onCompletion(() -> {
            log.info("✅ SSE stream completed for conversation: {}", conversationId);
            removeEmitter(conversationId);
        });

        emitter.onTimeout(() -> {
            log.warn("⏱️ SSE stream timed out for conversation: {}", conversationId);
            removeEmitter(conversationId);
        });

        emitter.onError((error) -> {
            log.error("❌ SSE stream error for conversation: {}", conversationId, error);
            removeEmitter(conversationId);
        });

        // Store emitter BEFORE sending events
        emitters.put(conversationId, emitter);

        try {
            // Send initial connection success event
            WorkflowEvent initialEvent = WorkflowEvent.builder()
                    .conversationId(conversationId)
                    .status(WorkflowEvent.WorkflowStatus.RUNNING)
                    .message("🔗 Connected to workflow stream")
                    .progress(0.0)
                    .build();

            emitter.send(SseEmitter.event()
                    .name("workflow-update")
                    .data(objectMapper.writeValueAsString(initialEvent)));

            // CRITICAL: Replay buffered events if any exist
            List<WorkflowEvent> bufferedEvents = eventBuffer.remove(conversationId);
            if (bufferedEvents != null && !bufferedEvents.isEmpty()) {
                log.info("🔄 Replaying {} buffered events for conversation: {}",
                        bufferedEvents.size(), conversationId);

                for (WorkflowEvent event : bufferedEvents) {
                    String json = objectMapper.writeValueAsString(event);
                    emitter.send(SseEmitter.event()
                            .name("workflow-update")
                            .data(json));
                }
            }

        } catch (IOException e) {
            log.error("Failed to send SSE events during stream creation", e);
            removeEmitter(conversationId);
        }

        return emitter;
    }

    /**
     * Send a workflow update to all connected clients for this conversation.
     *
     * CRITICAL: If client hasn't connected yet, buffer the event for replay.
     *
     * @param conversationId Conversation ID
     * @param event Workflow event to send
     */
    public void sendUpdate(String conversationId, WorkflowEvent event) {
        SseEmitter emitter = emitters.get(conversationId);

        // If no active connection, buffer the event for when client connects
        if (emitter == null) {
            List<WorkflowEvent> buffer = eventBuffer.computeIfAbsent(
                    conversationId,
                    k -> new ArrayList<>()
            );

            // Prevent memory leak: limit buffer size
            if (buffer.size() < MAX_BUFFERED_EVENTS) {
                buffer.add(event);
                log.debug("📦 Buffered SSE event (total: {}): conversationId={}, agent={}",
                        buffer.size(), conversationId, event.getAgent());
            } else {
                log.warn("⚠️ Event buffer full for conversation: {} (dropping event)", conversationId);
            }
            return;
        }

        // Send to connected client
        try {
            String json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event()
                    .name("workflow-update")
                    .data(json));

            log.debug("📤 Sent SSE update: conversationId={}, status={}, agent={}, message={}",
                    conversationId, event.getStatus(), event.getAgent(), event.getMessage());

        } catch (IOException e) {
            log.error("Failed to send SSE update for conversation: {}", conversationId, e);
            removeEmitter(conversationId);
        }
    }

    /**
     * Complete the workflow stream successfully.
     *
     * @param conversationId Conversation ID
     * @param message Completion message
     */
    public void complete(String conversationId, String message) {
        WorkflowEvent event = WorkflowEvent.completed(conversationId, message);
        sendUpdate(conversationId, event);

        // Complete the emitter
        SseEmitter emitter = emitters.get(conversationId);
        if (emitter != null) {
            emitter.complete();
            removeEmitter(conversationId);
        }

        log.info("✅ Workflow stream completed: {}", conversationId);
    }

    /**
     * Fail the workflow stream with an error.
     *
     * @param conversationId Conversation ID
     * @param error Error message
     */
    public void fail(String conversationId, String error) {
        WorkflowEvent event = WorkflowEvent.failed(conversationId, error);
        sendUpdate(conversationId, event);

        // Complete the emitter with error
        SseEmitter emitter = emitters.get(conversationId);
        if (emitter != null) {
            emitter.completeWithError(new RuntimeException(error));
            removeEmitter(conversationId);
        }

        log.error("❌ Workflow stream failed: {} - {}", conversationId, error);
    }

    /**
     * Remove emitter from active streams and clean up buffer.
     */
    private void removeEmitter(String conversationId) {
        emitters.remove(conversationId);
        eventBuffer.remove(conversationId);  // Clean up any remaining buffered events
        log.debug("🗑️ Removed SSE emitter for conversation: {}", conversationId);
    }

    /**
     * Check if there's an active stream for a conversation.
     */
    public boolean hasActiveStream(String conversationId) {
        return emitters.containsKey(conversationId);
    }

    /**
     * Get count of active streams (for monitoring).
     */
    public int getActiveStreamCount() {
        return emitters.size();
    }
}
