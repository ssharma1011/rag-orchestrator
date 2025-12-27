package com.purchasingpower.autoflow.model.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for tracking every LLM API call.
 * OBSERVABILITY: Enables cost tracking, prompt optimization, and debugging.
 */
@Entity
@Table(name = "LLM_REQUESTS")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMRequest {

    @Id
    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "interaction_id", length = 100)
    private String interactionId;  // Links to AGENT_INTERACTIONS

    @Column(name = "conversation_id", nullable = false, length = 100)
    private String conversationId;

    // LLM Details
    @Column(name = "provider", nullable = false, length = 50)
    private String provider;  // gemini, openai, anthropic

    @Column(name = "model", nullable = false, length = 100)
    private String model;  // gemini-1.5-pro, gpt-4, claude-3-opus

    @Column(name = "request_type", length = 50)
    private String requestType;  // text_generation, embedding, chat_completion

    // Timing
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    // Request/Response (CLOB)
    @Lob
    @Column(name = "prompt", nullable = false)
    private String prompt;

    @Column(name = "prompt_tokens")
    private Long promptTokens;

    @Lob
    @Column(name = "response")
    private String response;

    @Column(name = "response_tokens")
    private Long responseTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

    // Cost
    @Column(name = "cost_usd", precision = 10, scale = 6)
    private BigDecimal costUsd;

    // Status
    @Column(name = "status", length = 20)
    private String status;  // PENDING, SUCCESS, FAILED, TIMEOUT, RATE_LIMITED

    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    // Metadata
    @Column(name = "temperature", precision = 3, scale = 2)
    private BigDecimal temperature;

    @Column(name = "max_tokens")
    private Long maxTokens;

    // Audit
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }
}
