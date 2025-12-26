package com.purchasingpower.autoflow.model.conversation;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Workflow - Individual task execution within a conversation.
 *
 * A workflow represents one specific task or goal that the system is working on.
 * Multiple workflows can exist within a single conversation.
 *
 * Example:
 * - Workflow 1: Goal = "Add retry logic to Pinecone calls"
 * - Workflow 2: Goal = "Change timeout to 10 seconds"
 * - Both belong to same Conversation
 *
 * Lifecycle:
 * 1. User sends message → New Workflow created with goal
 * 2. Workflow executes agents → state_json updated
 * 3. Workflow completes/fails → completed_at set
 * 4. Conversation continues (can create new Workflow)
 */
@Data
@Entity
@Table(name = "WORKFLOWS")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workflow {

    @Id
    @Column(name = "workflow_id", nullable = false, length = 100)
    private String workflowId;

    /**
     * Parent conversation this workflow belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    /**
     * What this workflow is trying to accomplish.
     * Example: "Add retry logic to Pinecone calls"
     */
    @Lob
    @Column(name = "goal", columnDefinition = "CLOB")
    private String goal;

    /**
     * Current workflow status:
     * - RUNNING: Workflow is executing
     * - PAUSED: Waiting for user input
     * - COMPLETED: Workflow finished successfully
     * - FAILED: Workflow encountered an error
     * - CANCELLED: User cancelled the workflow
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /**
     * Which agent is currently executing (if RUNNING).
     * Example: "requirement_analyzer", "code_generator"
     */
    @Column(name = "current_agent", length = 50)
    private String currentAgent;

    /**
     * Complete WorkflowState object serialized as JSON.
     * This is a snapshot of the entire workflow state at any point in time.
     * Used for pause/resume and debugging.
     */
    @Lob
    @Column(name = "state_json", columnDefinition = "CLOB")
    private String stateJson;

    /**
     * When this workflow started executing.
     */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /**
     * When this workflow finished (completed/failed/cancelled).
     * NULL if still running.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // ================================================================
    // Lifecycle Hooks
    // ================================================================

    @PrePersist
    public void prePersist() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "RUNNING";
        }
    }

    // ================================================================
    // Helper Methods
    // ================================================================

    /**
     * Mark workflow as completed.
     */
    public void complete() {
        this.status = "COMPLETED";
        this.completedAt = LocalDateTime.now();
        this.currentAgent = null;  // No longer running any agent
    }

    /**
     * Mark workflow as failed with error message.
     */
    public void fail(String errorMessage) {
        this.status = "FAILED";
        this.completedAt = LocalDateTime.now();
        this.currentAgent = null;
    }

    /**
     * Cancel this workflow.
     */
    public void cancel() {
        this.status = "CANCELLED";
        this.completedAt = LocalDateTime.now();
        this.currentAgent = null;
    }

    /**
     * Pause this workflow (waiting for user input).
     */
    public void pause() {
        this.status = "PAUSED";
    }

    /**
     * Resume this workflow.
     */
    public void resume() {
        this.status = "RUNNING";
    }

    /**
     * Check if this workflow is still running.
     */
    public boolean isRunning() {
        return "RUNNING".equals(this.status);
    }

    /**
     * Check if this workflow is paused.
     */
    public boolean isPaused() {
        return "PAUSED".equals(this.status);
    }

    /**
     * Check if this workflow is complete.
     */
    public boolean isComplete() {
        return "COMPLETED".equals(this.status) ||
               "FAILED".equals(this.status) ||
               "CANCELLED".equals(this.status);
    }

    /**
     * Update current agent being executed.
     */
    public void setCurrentAgent(String agentName) {
        this.currentAgent = agentName;
    }

    /**
     * Get duration of this workflow in milliseconds.
     * Returns null if workflow is still running.
     */
    public Long getDurationMs() {
        if (completedAt == null) {
            return null;  // Still running
        }
        return java.time.Duration.between(startedAt, completedAt).toMillis();
    }
}
