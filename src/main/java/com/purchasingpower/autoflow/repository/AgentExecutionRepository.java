package com.purchasingpower.autoflow.repository;

import com.purchasingpower.autoflow.model.metrics.AgentExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for managing AgentExecution entities.
 *
 * Provides queries for agent performance monitoring, debugging, and analytics.
 */
@Repository
public interface AgentExecutionRepository extends JpaRepository<AgentExecution, Long> {

    /**
     * Find all executions for a specific conversation.
     */
    List<AgentExecution> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    /**
     * Find all executions for a specific agent.
     */
    List<AgentExecution> findByAgentNameOrderByCreatedAtDesc(String agentName);

    /**
     * Find failed executions.
     */
    List<AgentExecution> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * Find executions after a certain date.
     */
    List<AgentExecution> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime date);

    /**
     * Find executions by agent and date range.
     */
    List<AgentExecution> findByAgentNameAndCreatedAtBetween(
            String agentName,
            LocalDateTime start,
            LocalDateTime end
    );

    /**
     * Get agent performance statistics.
     */
    @Query("SELECT " +
           "ae.agentName as agentName, " +
           "COUNT(ae) as executionCount, " +
           "AVG(ae.latencyMs) as avgLatency, " +
           "MAX(ae.latencyMs) as maxLatency, " +
           "MIN(ae.latencyMs) as minLatency, " +
           "AVG(ae.tokenUsageInput + ae.tokenUsageOutput) as avgTokens, " +
           "SUM(CASE WHEN ae.status = 'SUCCESS' THEN 1 ELSE 0 END) as successCount, " +
           "SUM(CASE WHEN ae.status = 'FAILED' THEN 1 ELSE 0 END) as failureCount " +
           "FROM AgentExecution ae " +
           "WHERE ae.createdAt > :since " +
           "GROUP BY ae.agentName")
    List<Object[]> getAgentPerformanceStats(@Param("since") LocalDateTime since);

    /**
     * Get slowest executions.
     */
    @Query("SELECT ae FROM AgentExecution ae WHERE ae.latencyMs IS NOT NULL " +
           "ORDER BY ae.latencyMs DESC")
    List<AgentExecution> findSlowestExecutions();

    /**
     * Get total token usage by agent.
     */
    @Query("SELECT ae.agentName, " +
           "SUM(ae.tokenUsageInput) as totalInputTokens, " +
           "SUM(ae.tokenUsageOutput) as totalOutputTokens " +
           "FROM AgentExecution ae " +
           "WHERE ae.createdAt > :since " +
           "GROUP BY ae.agentName")
    List<Object[]> getTokenUsageByAgent(@Param("since") LocalDateTime since);

    /**
     * Delete old executions (for cleanup).
     */
    void deleteByCreatedAtBefore(LocalDateTime date);

    /**
     * Count executions by status.
     */
    long countByStatus(String status);
}
