package com.purchasingpower.autoflow.repository;

import com.purchasingpower.autoflow.model.workflow.WorkflowStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for persisting and querying workflow states.
 *
 * Used by WorkflowExecutionService to:
 * - Save workflow state (for pause/resume)
 * - Load workflow state (for resume)
 * - Query workflows by user/status
 */
@Repository
public interface WorkflowStateRepository extends JpaRepository<WorkflowStateEntity, Long> {

    /**
     * Find workflow by conversation ID.
     *
     * @param conversationId The unique conversation identifier
     * @return Optional workflow state entity
     */
    Optional<WorkflowStateEntity> findByConversationId(String conversationId);

    /**
     * Find all workflows for a user.
     *
     * @param userId The user ID
     * @return List of workflow states
     */
    List<WorkflowStateEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Find workflows by status.
     *
     * @param status The workflow status (RUNNING, PAUSED, etc.)
     * @return List of workflow states
     */
    List<WorkflowStateEntity> findByStatus(String status);

    /**
     * Find paused workflows (awaiting user input).
     *
     * @return List of paused workflows
     */
    @Query("SELECT w FROM WorkflowStateEntity w WHERE w.status = 'PAUSED' ORDER BY w.updatedAt DESC")
    List<WorkflowStateEntity> findPausedWorkflows();

    /**
     * Find running workflows (actively executing).
     *
     * @return List of running workflows
     */
    @Query("SELECT w FROM WorkflowStateEntity w WHERE w.status = 'RUNNING' ORDER BY w.updatedAt DESC")
    List<WorkflowStateEntity> findRunningWorkflows();

    /**
     * Find workflows that haven't been updated in a while (stale).
     * Used for cleanup/monitoring.
     *
     * @param cutoffTime Cutoff time (e.g., 1 hour ago)
     * @return List of stale workflows
     */
    @Query("SELECT w FROM WorkflowStateEntity w WHERE w.status = 'RUNNING' AND w.updatedAt < :cutoffTime")
    List<WorkflowStateEntity> findStaleWorkflows(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Delete old completed/failed workflows.
     * Used for cleanup.
     *
     * @param cutoffTime Cutoff time (e.g., 30 days ago)
     * @return Number of deleted records
     */
    @Query("DELETE FROM WorkflowStateEntity w WHERE w.status IN ('COMPLETED', 'FAILED', 'CANCELLED') AND w.updatedAt < :cutoffTime")
    int deleteOldWorkflows(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Count workflows by status.
     *
     * @param status The workflow status
     * @return Count of workflows
     */
    long countByStatus(String status);

    /**
     * Check if a conversation ID exists.
     *
     * @param conversationId The conversation ID
     * @return True if exists, false otherwise
     */
    boolean existsByConversationId(String conversationId);
}
