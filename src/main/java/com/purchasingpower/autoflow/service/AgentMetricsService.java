package com.purchasingpower.autoflow.service;

import com.purchasingpower.autoflow.model.metrics.AgentExecution;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for tracking and analyzing agent execution metrics.
 */
public interface AgentMetricsService {

    /**
     * Record an agent execution.
     */
    void recordExecution(AgentExecution execution);

    /**
     * Get all executions for a conversation.
     */
    List<AgentExecution> getExecutionsForConversation(String conversationId);

    /**
     * Get agent performance statistics.
     */
    Map<String, Object> getAgentPerformanceStats(LocalDateTime since);

    /**
     * Get slowest agent executions.
     */
    List<AgentExecution> getSlowestExecutions(int limit);

    /**
     * Get failed executions.
     */
    List<AgentExecution> getFailedExecutions(int limit);

    /**
     * Get token usage by agent.
     */
    Map<String, Map<String, Long>> getTokenUsageByAgent(LocalDateTime since);
}
