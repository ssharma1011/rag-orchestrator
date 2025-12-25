package com.purchasingpower.autoflow.model.metrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tracks every LLM API call for observability and cost analysis.
 *
 * RAGAS Framework Alignment:
 * - Context Relevance: How well the retrieved context matches the query
 * - Answer Relevance: How well the LLM response addresses the prompt
 * - Faithfulness: Whether the response is grounded in provided context
 * - Response Quality: Overall quality metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMCallMetrics {

    // ================================================================
    // CALL IDENTIFICATION
    // ================================================================

    /**
     * Unique ID for this specific LLM call
     */
    private String callId;

    /**
     * Which agent made this call
     */
    private String agentName;

    /**
     * Workflow/conversation this call belongs to
     */
    private String conversationId;

    /**
     * Timestamp when call was made
     */
    private LocalDateTime timestamp;

    // ================================================================
    // REQUEST DETAILS
    // ================================================================

    /**
     * Model used (e.g., gemini-1.5-pro)
     */
    private String model;

    /**
     * Full prompt sent to LLM
     */
    private String prompt;

    /**
     * Prompt length in characters
     */
    private int promptLength;

    /**
     * Temperature used
     */
    private double temperature;

    /**
     * Max tokens requested
     */
    private Integer maxTokens;

    // ================================================================
    // RESPONSE DETAILS
    // ================================================================

    /**
     * Raw LLM response
     */
    private String response;

    /**
     * Response length in characters
     */
    private int responseLength;

    /**
     * Whether call succeeded
     */
    private boolean success;

    /**
     * Error message if failed
     */
    private String errorMessage;

    // ================================================================
    // PERFORMANCE METRICS
    // ================================================================

    /**
     * Time to first token (ms)
     */
    private Long timeToFirstToken;

    /**
     * Total latency (ms)
     */
    private long latencyMs;

    /**
     * Tokens per second
     */
    private Double tokensPerSecond;

    // ================================================================
    // TOKEN USAGE (Cost Tracking)
    // ================================================================

    /**
     * Input tokens consumed
     */
    private int inputTokens;

    /**
     * Output tokens generated
     */
    private int outputTokens;

    /**
     * Total tokens (input + output)
     */
    private int totalTokens;

    /**
     * Estimated cost in USD
     * Gemini 1.5 Pro: $0.00125 per 1K input, $0.005 per 1K output
     */
    private Double estimatedCost;  // Changed to Double to match Oracle FLOAT(53)

    // ================================================================
    // RAGAS METRICS (Quality Assessment)
    // ================================================================

    /**
     * Context Relevance Score (0.0 - 1.0)
     * How relevant was the retrieved context to the query?
     */
    private Double contextRelevanceScore;

    /**
     * Answer Relevance Score (0.0 - 1.0)
     * How well does the response address the prompt?
     */
    private Double answerRelevanceScore;

    /**
     * Faithfulness Score (0.0 - 1.0)
     * Is the response grounded in provided context (not hallucinated)?
     */
    private Double faithfulnessScore;

    /**
     * Response Quality Score (0.0 - 1.0)
     * Overall quality assessment
     */
    private Double responseQualityScore;

    // ================================================================
    // RETRY & ERROR HANDLING
    // ================================================================

    /**
     * Number of retry attempts
     */
    private int retryCount;

    /**
     * Whether this was a retry of a previous failed call
     */
    private boolean isRetry;

    /**
     * HTTP status code
     */
    private Integer httpStatusCode;

    // ================================================================
    // HELPER METHODS
    // ================================================================

    /**
     * Calculate estimated cost based on token usage.
     * Gemini 1.5 Pro pricing (as of Dec 2024):
     * - Input: $0.00125 per 1K tokens
     * - Output: $0.005 per 1K tokens
     */
    public double calculateCost() {
        double inputCost = (inputTokens / 1000.0) * 0.00125;
        double outputCost = (outputTokens / 1000.0) * 0.005;
        return inputCost + outputCost;
    }

    /**
     * Calculate tokens per second throughput
     */
    public double calculateTokensPerSecond() {
        if (latencyMs == 0) return 0.0;
        return (totalTokens * 1000.0) / latencyMs;
    }
}