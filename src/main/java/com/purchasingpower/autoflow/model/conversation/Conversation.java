package com.purchasingpower.autoflow.model.conversation;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Conversation - Long-lived conversation session that can span multiple workflows.
 *
 * A conversation represents the entire interaction between a developer and AutoFlow
 * for a specific repository. It can contain multiple workflows (tasks) over time.
 *
 * Example:
 * - User: "Add retry logic" → Workflow 1 executes
 * - User: "Change timeout to 10s" → Workflow 2 executes
 * - Both workflows belong to same Conversation
 */
@Data
@Entity
@Table(name = "CONVERSATIONS")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @Column(name = "conversation_id", nullable = false, length = 100)
    private String conversationId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "repo_url", length = 500)
    private String repoUrl;

    @Column(name = "repo_name", length = 200)
    private String repoName;

    /**
     * Current conversation mode:
     * - EXPLORE: User asking questions, exploring codebase
     * - DEBUG: User investigating a bug/issue
     * - IMPLEMENT: User wants code changes
     * - REVIEW: Reviewing proposed changes
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_mode", length = 20)
    private ConversationMode mode;

    /**
     * Is this conversation still active?
     * - 1 = active (user can continue conversation)
     * - 0 = closed (conversation ended)
     */
    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_activity", nullable = false)
    private LocalDateTime lastActivity;

    /**
     * Workflows that belong to this conversation.
     * One conversation can have many workflows over time.
     */
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Workflow> workflows = new ArrayList<>();

    /**
     * Messages in this conversation (from CONVERSATION_MESSAGES table).
     * Links to existing ConversationMessage entities via conversation_id.
     *
     * FIXED: Column name was "conversation_id_ref" but actual DB column is "conversation_id"
     */
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    @OrderBy("timestamp ASC")
    private List<ConversationMessage> messages = new ArrayList<>();

    /**
     * Conversation mode enum.
     */
    public enum ConversationMode {
        EXPLORE,    // Exploring/understanding codebase
        DEBUG,      // Investigating bugs
        IMPLEMENT,  // Building features/fixes
        REVIEW      // Reviewing changes
    }

    // ================================================================
    // Lifecycle Hooks
    // ================================================================

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lastActivity == null) {
            lastActivity = LocalDateTime.now();
        }
        if (mode == null) {
            mode = ConversationMode.IMPLEMENT;  // Default mode
        }
    }

    @PreUpdate
    public void preUpdate() {
        lastActivity = LocalDateTime.now();
    }

    // ================================================================
    // Helper Methods
    // ================================================================

    /**
     * Add a message to this conversation.
     */
    public void addMessage(ConversationMessage message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        message.setTimestamp(LocalDateTime.now());
        this.messages.add(message);
        this.lastActivity = LocalDateTime.now();
    }

    /**
     * Add a workflow to this conversation.
     */
    public void addWorkflow(Workflow workflow) {
        if (this.workflows == null) {
            this.workflows = new ArrayList<>();
        }
        workflow.setConversation(this);
        this.workflows.add(workflow);
        this.lastActivity = LocalDateTime.now();
    }

    /**
     * Get the most recent workflow.
     */
    public Workflow getLastWorkflow() {
        if (workflows == null || workflows.isEmpty()) {
            return null;
        }
        return workflows.get(workflows.size() - 1);
    }

    /**
     * Check if this conversation has any running workflows.
     */
    public boolean hasRunningWorkflow() {
        if (workflows == null) {
            return false;
        }
        return workflows.stream()
                .anyMatch(w -> "RUNNING".equals(w.getStatus()) || "PAUSED".equals(w.getStatus()));
    }

    /**
     * Close this conversation.
     */
    public void close() {
        this.isActive = false;
        this.lastActivity = LocalDateTime.now();
    }

    /**
     * Reopen this conversation.
     */
    public void reopen() {
        this.isActive = true;
        this.lastActivity = LocalDateTime.now();
    }
}
