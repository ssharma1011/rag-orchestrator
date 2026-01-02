package com.purchasingpower.autoflow.controller;

import com.purchasingpower.autoflow.service.WorkflowStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST controller for Server-Sent Events (SSE) streaming.
 *
 * @deprecated Use {@link com.purchasingpower.autoflow.api.ChatController#stream} instead.
 * GET /api/v1/chat/{conversationId}/stream
 *
 * This controller will be removed in a future release.
 */
@Deprecated(since = "2.0.0", forRemoval = true)
@Slf4j
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Allow CORS for SSE
public class StreamController {

    private final WorkflowStreamService streamService;

    /**
     * Stream workflow updates via Server-Sent Events.
     *
     * @param conversationId Conversation ID to stream updates for
     * @return SseEmitter that sends workflow events
     */
    @GetMapping(value = "/{conversationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWorkflowUpdates(@PathVariable String conversationId) {
        log.info("📡 Client connected to SSE stream: {}", conversationId);

        try {
            return streamService.createStream(conversationId);

        } catch (Exception e) {
            log.error("Failed to create SSE stream for conversation: {}", conversationId, e);
            throw new RuntimeException("Failed to create stream: " + e.getMessage());
        }
    }

    /**
     * Health check endpoint to verify SSE service is running.
     *
     * @return Status information
     */
    @GetMapping("/stream/health")
    public StreamHealthResponse getStreamHealth() {
        return new StreamHealthResponse(
                "UP",
                streamService.getActiveStreamCount(),
                "SSE streaming service is operational"
        );
    }

    /**
     * Response DTO for health check.
     */
    public record StreamHealthResponse(
            String status,
            int activeStreams,
            String message
    ) {}
}
