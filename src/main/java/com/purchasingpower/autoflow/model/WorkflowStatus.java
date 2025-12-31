package com.purchasingpower.autoflow.model;

/**
 * Enumeration of all possible workflow execution states.
 *
 * <p>Replaces string literals to provide:
 * - Compile-time type safety
 * - IDE autocomplete
 * - Prevention of typos ("COMPLTED", "COMPELTED")
 * - Explicit state machine definition
 *
 * <p>State Transitions:
 * <pre>
 * PENDING → RUNNING → COMPLETED
 *             ↓
 *          PAUSED → RUNNING
 *             ↓
 *          FAILED
 *             ↓
 *          CANCELLED
 * </pre>
 *
 * @see WorkflowState
 * @see AutoFlowWorkflow
 */
public enum WorkflowStatus {

    /**
     * Initial state - workflow created but not yet started.
     */
    PENDING,

    /**
     * Workflow is actively executing agents.
     */
    RUNNING,

    /**
     * Workflow temporarily paused waiting for user input.
     * Can be resumed to RUNNING state.
     */
    PAUSED,

    /**
     * Workflow completed successfully.
     * Terminal state - cannot transition further.
     */
    COMPLETED,

    /**
     * Workflow failed due to error.
     * Terminal state - cannot transition further.
     */
    FAILED,

    /**
     * Workflow cancelled by user or system.
     * Terminal state - cannot transition further.
     */
    CANCELLED;

    /**
     * Check if this status represents a terminal state.
     * Terminal states cannot transition to any other state.
     *
     * @return true if terminal (COMPLETED, FAILED, CANCELLED)
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    /**
     * Check if this status represents an active workflow.
     * Active workflows are currently executing or can be resumed.
     *
     * @return true if active (RUNNING, PAUSED)
     */
    public boolean isActive() {
        return this == RUNNING || this == PAUSED;
    }

    /**
     * Check if workflow can be cancelled in this state.
     *
     * @return true if cancellable (PENDING, RUNNING, PAUSED)
     */
    public boolean isCancellable() {
        return this == PENDING || this == RUNNING || this == PAUSED;
    }
}
