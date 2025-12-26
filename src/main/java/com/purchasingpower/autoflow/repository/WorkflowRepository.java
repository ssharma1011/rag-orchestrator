package com.purchasingpower.autoflow.repository;

import com.purchasingpower.autoflow.model.conversation.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing Workflow entities.
 *
 * Workflows represent individual task executions within conversations.
 */
@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, String> {

    /**
     * Find all workflows for a specific conversation.
     */
    List<Workflow> findByConversation_ConversationIdOrderByStartedAtDesc(String conversationId);

    /**
     * Find running workflows for a conversation.
     */
    List<Workflow> findByConversation_ConversationIdAndStatusIn(
            String conversationId,
            List<String> statuses
    );

    /**
     * Find all failed workflows that can be retried.
     */
    List<Workflow> findByStatusInOrderByStartedAtDesc(List<String> statuses);

    /**
     * Find the most recent workflow for a conversation.
     */
    @Query("SELECT w FROM Workflow w WHERE w.conversation.conversationId = :conversationId " +
           "ORDER BY w.startedAt DESC")
    Optional<Workflow> findMostRecentWorkflow(@Param("conversationId") String conversationId);

    /**
     * Find workflow by ID with conversation eagerly loaded.
     */
    @Query("SELECT w FROM Workflow w LEFT JOIN FETCH w.conversation WHERE w.workflowId = :workflowId")
    Optional<Workflow> findByIdWithConversation(@Param("workflowId") String workflowId);

    /**
     * Count workflows by status.
     */
    long countByStatus(String status);

    /**
     * Count workflows for a conversation.
     */
    long countByConversation_ConversationId(String conversationId);
}
