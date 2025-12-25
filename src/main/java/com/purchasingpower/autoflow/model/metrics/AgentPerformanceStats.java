package com.purchasingpower.autoflow.model.metrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Performance statistics for a specific agent.
 * Used for dashboard analytics and agent comparison.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPerformanceStats {

    /**
     * Agent name (e.g., "requirement_analyzer")
     */
    private String agentName;

    /**
     * Total tokens consumed by this agent across all calls
     */
    private long totalTokensUsed;

    /**
     * Average latency in milliseconds
     */
    private double averageLatencyMs;

    /**
     * Success rate (0-100%)
     */
    private double successRate;

    /**
     * Average context relevance score (0.0-1.0)
     * RAGAS metric: How relevant is the retrieved context?
     */
    private Double avgContextRelevance;

    /**
     * Average answer relevance score (0.0-1.0)
     * RAGAS metric: How well does response address the prompt?
     */
    private Double avgAnswerRelevance;

    /**
     * Average faithfulness score (0.0-1.0)
     * RAGAS metric: Is response grounded in facts (not hallucinated)?
     */
    private Double avgFaithfulness;

    /**
     * Average response quality score (0.0-1.0)
     * Overall quality assessment
     */
    private Double avgResponseQuality;

    /**
     * Total number of calls made by this agent
     */
    private long totalCalls;

    /**
     * Number of failed calls
     */
    private long failedCalls;

    /**
     * Total cost incurred by this agent (USD)
     */
    private double totalCost;
}