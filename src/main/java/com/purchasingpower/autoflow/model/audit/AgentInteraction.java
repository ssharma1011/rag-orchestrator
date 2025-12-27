package com.purchasingpower.autoflow.model.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for tracking every agent execution.
 * OBSERVABILITY: Enables debugging, performance analysis, and audit trails.
 */
@Entity
@Table(name = "AGENT_INTERACTIONS")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentInteraction {

    @Id
    @Column(name = "interaction_id", length = 100)
    private String interactionId;

    @Column(name = "conversation_id", nullable = false, length = 100)
    private String conversationId;

    @Column(name = "workflow_id", length = 100)
    private String workflowId;

    @Column(name = "agent_name", nullable = false, length = 50)
    private String agentName;  // requirement_analyzer, code_indexer, documentation_agent, etc.

    // Timing
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    // Input/Output (CLOB)
    @Lob
    @Column(name = "input_state")
    private String inputState;  // JSON of WorkflowState before execution

    @Lob
    @Column(name = "output_state")
    private String outputState; // JSON of WorkflowState after execution

    @Lob
    @Column(name = "agent_decision")
    private String agentDecision; // JSON of AgentDecision

    // Status
    @Column(name = "status", length = 20)
    private String status;  // RUNNING, SUCCESS, FAILED, TIMEOUT

    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    @Lob
    @Column(name = "error_stack_trace")
    private String errorStackTrace;

    // Metrics
    @Column(name = "tokens_used")
    private Long tokensUsed;

    @Column(name = "cost_usd", precision = 10, scale = 6)
    private BigDecimal costUsd;

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
