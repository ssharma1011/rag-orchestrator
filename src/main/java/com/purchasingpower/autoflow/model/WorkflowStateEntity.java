package com.purchasingpower.autoflow.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA Entity for persisting workflow state.
 * 
 * Stores the entire WorkflowState object as JSON in a CLOB field.
 * This allows workflow to pause and resume seamlessly.
 * 
 * Table: WORKFLOW_STATES
 */
@Entity
@Table(name = "WORKFLOW_STATES", indexes = {
        @Index(name = "idx_workflow_conversation", columnList = "conversation_id", unique = true),
        @Index(name = "idx_workflow_user", columnList = "user_id"),
        @Index(name = "idx_workflow_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique conversation identifier.
     * Same as WorkflowState.conversationId
     */
    @Column(name = "conversation_id", nullable = false, unique = true, length = 100)
    private String conversationId;

    /**
     * User who started the workflow.
     */
    @Column(name = "user_id", length = 100)
    private String userId;

    /**
     * Current workflow status.
     * Values: RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /**
     * Current agent being executed.
     * Examples: "requirement_analyzer", "code_generator", "ask_developer"
     */
    @Column(name = "current_agent", length = 50)
    private String currentAgent;

    /**
     * Complete workflow state as JSON.
     * Stores the entire WorkflowState object.
     */
    @Lob
    @Column(name = "state_json", nullable = false)
    private String stateJson;

    /**
     * When workflow was created.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * When workflow was last updated.
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
