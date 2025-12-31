package com.purchasingpower.autoflow.model.dto;

import com.purchasingpower.autoflow.model.metrics.LLMCallMetrics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for conversation metrics API.
 *
 * Contains aggregated metrics for all LLM calls within a
 * specific conversation, including total cost, tokens,
 * latency, and detailed call information.
 *
 * @see com.purchasingpower.autoflow.controller.LLMMetricsController
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMetricsResponse {
    private String conversationId;
    private int totalCalls;
    private long totalTokens;
    private double totalCost;
    private double averageLatencyMs;
    private List<LLMCallMetrics> calls;
}
