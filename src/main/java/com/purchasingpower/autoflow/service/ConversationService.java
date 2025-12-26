package com.purchasingpower.autoflow.service;

import com.purchasingpower.autoflow.model.conversation.Conversation;
import com.purchasingpower.autoflow.model.conversation.ConversationMessage;
import com.purchasingpower.autoflow.model.conversation.Workflow;
import com.purchasingpower.autoflow.workflow.state.WorkflowState;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing conversations and their persistence.
 */
public interface ConversationService {

    /**
     * Create a new conversation.
     */
    Conversation createConversation(String conversationId, String userId, String repoUrl);

    /**
     * Get conversation by ID.
     */
    Optional<Conversation> getConversation(String conversationId);

    /**
     * Get conversation with workflows eagerly loaded.
     */
    Optional<Conversation> getConversationWithWorkflows(String conversationId);

    /**
     * Get conversation with messages eagerly loaded.
     */
    Optional<Conversation> getConversationWithMessages(String conversationId);

    /**
     * Get all active conversations for a user.
     */
    List<Conversation> getActiveConversations(String userId);

    /**
     * Save or update conversation.
     */
    Conversation saveConversation(Conversation conversation);

    /**
     * Save conversation from WorkflowState.
     * Extracts conversation history and persists to normalized tables.
     */
    void saveConversationFromWorkflowState(WorkflowState state);

    /**
     * Add a message to conversation.
     */
    void addMessage(String conversationId, String role, String content);

    /**
     * Add a workflow to conversation.
     */
    Workflow addWorkflow(String conversationId, String workflowId, String goal);

    /**
     * Update workflow status.
     */
    void updateWorkflowStatus(String workflowId, String status);

    /**
     * Complete a workflow.
     */
    void completeWorkflow(String workflowId);

    /**
     * Fail a workflow.
     */
    void failWorkflow(String workflowId, String errorMessage);

    /**
     * Close a conversation.
     */
    void closeConversation(String conversationId);

    /**
     * Reopen a conversation.
     */
    void reopenConversation(String conversationId);

    /**
     * Check if conversation has running workflows.
     */
    boolean hasRunningWorkflow(String conversationId);
}
