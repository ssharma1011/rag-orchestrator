package com.purchasingpower.autoflow.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.purchasingpower.autoflow.model.WorkflowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Server-Sent Event (SSE) payload for workflow updates.
 *
 * Sent to frontend clients to provide real-time progress updates
 * during workflow execution.
 *
 * Example usage:
 * <pre>
 * WorkflowEvent event = WorkflowEvent.builder()
 *     .conversationId("conv-123")
 *     .status(WorkflowStatus.RUNNING)
 *     .agent("requirement_analyzer")
 *     .message("📋 Analyzing requirement...")
 *     .progress(0.1)
 *     .build();
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowEvent {

    /**
     * Conversation ID this event belongs to.
     */
    private String conversationId;

    /**
     * Current workflow status.
     */
    private WorkflowStatus status;

    /**
     * Current agent executing (if applicable).
     * Examples: "requirement_analyzer", "code_indexer", "documentation_agent"
     */
    private String agent;

    /**
     * Human-readable status message.
     * Examples:
     * - "📋 Analyzing requirement..."
     * - "📦 Indexing codebase (5000 files)..."
     * - "🔍 Searching for relevant code..."
     * - "📚 Generating documentation..."
     * - "✅ Workflow completed"
     */
    private String message;

    /**
     * Progress percentage (0.0 to 1.0).
     * - 0.0 = Just started
     * - 0.5 = 50% complete
     * - 1.0 = Fully complete
     */
    private Double progress;

    /**
     * Error message if status = FAILED.
     */
    private String error;

    /**
     * Additional metadata (optional).
     */
    private Object metadata;

    // ================================================================
    // Builder Helpers
    // ================================================================

    /**
     * Create a RUNNING event.
     */
    public static WorkflowEvent running(String conversationId, String agent, String message, double progress) {
        return WorkflowEvent.builder()
                .conversationId(conversationId)
                .status(WorkflowStatus.RUNNING)
                .agent(agent)
                .message(message)
                .progress(progress)
                .build();
    }

    /**
     * Create a COMPLETED event.
     */
    public static WorkflowEvent completed(String conversationId, String message) {
        return WorkflowEvent.builder()
                .conversationId(conversationId)
                .status(WorkflowStatus.COMPLETED)
                .message(message)
                .progress(1.0)
                .build();
    }

    /**
     * Create a FAILED event.
     */
    public static WorkflowEvent failed(String conversationId, String error) {
        return WorkflowEvent.builder()
                .conversationId(conversationId)
                .status(WorkflowStatus.FAILED)
                .error(error)
                .message("❌ Workflow failed: " + error)
                .build();
    }

    /**
     * Create a PAUSED event.
     */
    public static WorkflowEvent paused(String conversationId, String message) {
        return WorkflowEvent.builder()
                .conversationId(conversationId)
                .status(WorkflowStatus.PAUSED)
                .message(message)
                .build();
    }
}
