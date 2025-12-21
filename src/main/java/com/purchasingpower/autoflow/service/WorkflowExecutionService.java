package com.purchasingpower.autoflow.service;

import com.purchasingpower.autoflow.workflow.state.WorkflowState;

/**
 * Service for executing and managing AutoFlow workflows.
 *
 * Handles workflow lifecycle:
 * - Starting new workflows
 * - Resuming paused workflows
 * - Getting workflow state
 * - Cancelling workflows
 */
public interface WorkflowExecutionService {

    /**
     * Start a new workflow with the given initial state.
     *
     * @param initialState Initial workflow state with requirement, repo URL, etc.
     * @return Updated workflow state after initialization
     */
    WorkflowState startWorkflow(WorkflowState initialState);

    /**
     * Resume a paused workflow after user provides input.
     *
     * @param state Current workflow state with user's response
     * @return Updated workflow state after resumption
     */
    WorkflowState resumeWorkflow(WorkflowState state);

    /**
     * Get the current state of a workflow.
     *
     * @param conversationId Unique workflow identifier
     * @return Current workflow state, or null if not found
     */
    WorkflowState getWorkflowState(String conversationId);

    /**
     * Cancel a running workflow.
     *
     * @param conversationId Unique workflow identifier
     */
    void cancelWorkflow(String conversationId);
}
