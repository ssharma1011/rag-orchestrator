package com.purchasingpower.autoflow.repository;

import com.purchasingpower.autoflow.model.conversation.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing Conversation entities.
 *
 * Conversations are long-lived sessions that can span multiple workflows.
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {

    /**
     * Find all active conversations for a specific user.
     */
    List<Conversation> findByUserIdAndIsActiveTrue(String userId);

    /**
     * Find all conversations for a specific user (active and closed).
     */
    List<Conversation> findByUserIdOrderByLastActivityDesc(String userId);

    /**
     * Find active conversations for a specific repository.
     */
    List<Conversation> findByRepoNameAndIsActiveTrue(String repoName);

    /**
     * Find conversations that haven't been active since a certain date.
     */
    List<Conversation> findByLastActivityBeforeAndIsActiveTrue(LocalDateTime date);

    /**
     * Find conversation by ID with workflows eagerly loaded.
     */
    @Query("SELECT c FROM Conversation c LEFT JOIN FETCH c.workflows WHERE c.conversationId = :conversationId")
    Optional<Conversation> findByIdWithWorkflows(@Param("conversationId") String conversationId);

    /**
     * Find conversation by ID with messages eagerly loaded.
     */
    @Query("SELECT c FROM Conversation c LEFT JOIN FETCH c.messages WHERE c.conversationId = :conversationId")
    Optional<Conversation> findByIdWithMessages(@Param("conversationId") String conversationId);

    /**
     * Find conversations with running workflows.
     */
    @Query("SELECT DISTINCT c FROM Conversation c JOIN c.workflows w WHERE w.status IN ('RUNNING', 'PAUSED')")
    List<Conversation> findConversationsWithRunningWorkflows();

    /**
     * Count active conversations for a user.
     */
    long countByUserIdAndIsActiveTrue(String userId);
}
