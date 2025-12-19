package com.purchasingpower.autoflow.service;

import com.purchasingpower.autoflow.model.conversation.ConversationContext;

/**
 * Service for managing interactive conversations between developers and LLM.
 * Handles message processing, state management, and conversation flow.
 */
public interface ConversationService {

    /**
     * Handle a message from developer (via JIRA comment with @autoflow).
     *
     * @param issueKey JIRA issue key (e.g., "PROJ-123")
     * @param message The message content (without @autoflow prefix)
     * @param author Developer's display name
     */
    void handleMessage(String issueKey, String message, String author);

    /**
     * Get conversation context for a specific issue.
     *
     * @param issueKey JIRA issue key
     * @return ConversationContext or null if not found
     */
    ConversationContext getConversation(String issueKey);

    /**
     * Check if a conversation exists for an issue.
     *
     * @param issueKey JIRA issue key
     * @return true if conversation exists
     */
    boolean conversationExists(String issueKey);

    /**
     * Delete conversation (cleanup after completion).
     *
     * @param issueKey JIRA issue key
     */
    void deleteConversation(String issueKey);
}