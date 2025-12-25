package com.purchasingpower.autoflow.repository;

import com.purchasingpower.autoflow.model.metrics.LLMCallMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for LLM call metrics tracking.
 * Enables cost analysis, performance monitoring, and quality assessment.
 */
@Repository
public interface LLMCallMetricsRepository extends JpaRepository<LLMCallMetricsEntity, Long> {

    /**
     * Find all calls for a specific conversation
     */
    List<LLMCallMetricsEntity> findByConversationIdOrderByTimestampDesc(String conversationId);

    /**
     * Find all calls by a specific agent
     */
    List<LLMCallMetricsEntity> findByAgentNameOrderByTimestampDesc(String agentName);

    /**
     * Find failed calls for debugging
     */
    List<LLMCallMetricsEntity> findBySuccessFalseOrderByTimestampDesc();

    /**
     * Get calls within a time range
     */
    List<LLMCallMetricsEntity> findByTimestampBetweenOrderByTimestampDesc(
            LocalDateTime start, 
            LocalDateTime end
    );

    /**
     * Calculate total cost for a conversation
     */
    @Query("SELECT SUM(m.estimatedCost) FROM LLMCallMetricsEntity m WHERE m.conversationId = :conversationId")
    Double calculateTotalCostForConversation(@Param("conversationId") String conversationId);

    /**
     * Calculate total tokens used by agent
     */
    @Query("SELECT SUM(m.totalTokens) FROM LLMCallMetricsEntity m WHERE m.agentName = :agentName")
    Long calculateTotalTokensByAgent(@Param("agentName") String agentName);

    /**
     * Get average latency by agent
     */
    @Query("SELECT AVG(m.latencyMs) FROM LLMCallMetricsEntity m WHERE m.agentName = :agentName")
    Double getAverageLatencyByAgent(@Param("agentName") String agentName);

    /**
     * Get success rate by agent
     */
//    @Query("SELECT COUNT(m) * 100.0 / (SELECT COUNT(mm) FROM LLMCallMetricsEntity mm WHERE mm.agentName = :agentName) " +
//           "FROM LLMCallMetricsEntity m WHERE m.agentName = :agentName AND m.success = true")
//
    @Query("SELECT (SUM(CASE WHEN m.success = true THEN 1 ELSE 0 END) * 100.0) / COUNT(m) FROM LLMCallMetricsEntity m WHERE m.agentName = :agentName")
    Double getSuccessRateByAgent(@Param("agentName") String agentName);

    /**
     * Get calls with high retry count (indicating problems)
     */
    @Query("SELECT m FROM LLMCallMetricsEntity m WHERE m.retryCount > :threshold ORDER BY m.timestamp DESC")
    List<LLMCallMetricsEntity> findHighRetryCountCalls(@Param("threshold") int threshold);

    /**
     * Get total cost over time period
     */
    @Query("SELECT SUM(m.estimatedCost) FROM LLMCallMetricsEntity m WHERE m.timestamp BETWEEN :start AND :end")
    Double calculateCostInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Get average RAGAS scores by agent
     */
    @Query("SELECT AVG(m.contextRelevanceScore), AVG(m.answerRelevanceScore), " +
           "AVG(m.faithfulnessScore), AVG(m.responseQualityScore) " +
           "FROM LLMCallMetricsEntity m WHERE m.agentName = :agentName")
    Object[] getAverageRAGASScoresByAgent(@Param("agentName") String agentName);
}
