package com.purchasingpower.autoflow.model.metrics;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA Entity for persisting LLM call metrics to Oracle database.
 * Maps to the llm_call_metrics table.
 */
@Entity
@Table(name = "llm_call_metrics")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LLMCallMetricsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "llm_metrics_seq")
    @SequenceGenerator(name = "llm_metrics_seq", sequenceName = "llm_metrics_seq", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    // ================================================================
    // CALL IDENTIFICATION
    // ================================================================

    @Column(name = "call_id", unique = true, nullable = false, length = 100)
    private String callId;

    @Column(name = "agent_name", nullable = false, length = 100)
    private String agentName;

    @Column(name = "conversation_id", length = 100)
    private String conversationId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    // ================================================================
    // REQUEST DETAILS
    // ================================================================

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Lob
    @Column(name = "prompt")
    private String prompt;

    @Column(name = "prompt_length")
    private Integer promptLength;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    // ================================================================
    // RESPONSE DETAILS
    // ================================================================

    @Lob
    @Column(name = "response")
    private String response;

    @Column(name = "response_length")
    private Integer responseLength;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    // ================================================================
    // PERFORMANCE METRICS
    // ================================================================

    @Column(name = "time_to_first_token")
    private Long timeToFirstToken;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "tokens_per_second")
    private Double tokensPerSecond;  // FLOAT(53) in Oracle

    // ================================================================
    // TOKEN USAGE (COST TRACKING)
    // ================================================================

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "estimated_cost")
    private Double estimatedCost;  // FLOAT(53) in Oracle - Changed to Double

    // ================================================================
    // RAGAS QUALITY METRICS
    // ================================================================

    @Column(name = "context_relevance_score")
    private Double contextRelevanceScore;  // FLOAT(53) in Oracle

    @Column(name = "answer_relevance_score")
    private Double answerRelevanceScore;  // FLOAT(53) in Oracle

    @Column(name = "faithfulness_score")
    private Double faithfulnessScore;  // FLOAT(53) in Oracle

    @Column(name = "response_quality_score")
    private Double responseQualityScore;  // FLOAT(53) in Oracle

    // ================================================================
    // RETRY & ERROR HANDLING
    // ================================================================

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "is_retry")
    private boolean isRetry;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    // ================================================================
    // AUDIT
    // ================================================================

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}