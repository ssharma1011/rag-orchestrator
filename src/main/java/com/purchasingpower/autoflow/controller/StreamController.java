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
 * Provides real-time workflow updates to replace polling.
 *
 * Frontend usage:
 * <pre>
 * const eventSource = new EventSource(
 *   `http://localhost:8080/api/v1/workflows/${conversationId}/stream`
 * );
 *
 * eventSource.onmessage = (event) => {
 *   const data = JSON.parse(event.data);
 *   console.log(`[${data.agent}] ${data.message} (${data.progress * 100}%)`);
 *
 *   if (data.status === 'COMPLETED') {
 *     eventSource.close();
 *     showResults();
 *   }
 * };
 * </pre>
 */
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
