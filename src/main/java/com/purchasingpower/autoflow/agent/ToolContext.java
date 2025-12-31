package com.purchasingpower.autoflow.agent;

import com.purchasingpower.autoflow.model.conversation.Conversation;

import java.util.List;

/**
 * Context provided to tools during execution.
 *
 * <p>Contains all the contextual information a tool might need:
 * conversation history, active repositories, user preferences, etc.
 *
 * @since 2.0.0
 */
public interface ToolContext {

    /**
     * The current conversation.
     */
    Conversation getConversation();

    /**
     * Active repository IDs for this context.
     */
    List<String> getRepositoryIds();

    /**
     * Get a context variable by key.
     *
     * @param key Variable key
     * @return Variable value or null
     */
    Object getVariable(String key);

    /**
     * Set a context variable.
     *
     * @param key Variable key
     * @param value Variable value
     */
    void setVariable(String key, Object value);

    /**
     * Get recent search results (for follow-up queries).
     */
    List<?> getRecentSearchResults();

    /**
     * Get recent code modifications (for undo/verification).
     */
    List<?> getRecentModifications();
}
