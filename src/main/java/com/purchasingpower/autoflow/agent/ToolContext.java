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
     * Repository URL for the current conversation.
     */
    String getRepositoryUrl();

    /**
     * Branch name for the repository (e.g., "main", "develop").
     */
    String getBranch();

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

    /**
     * Record a tool execution for tracking and learning.
     *
     * @param toolName Name of the tool that was executed
     * @param result Result of the execution
     * @param userFeedback Optional user feedback ("better", "more detail", etc.)
     */
    void recordToolExecution(String toolName, Object result, String userFeedback);

    /**
     * Get the number of times a specific tool has been executed in this context.
     *
     * @param toolName Name of the tool
     * @return Execution count
     */
    int getToolExecutionCount(String toolName);

    /**
     * Check if there's negative feedback in recent conversation messages.
     * Looks for phrases like "better", "more detail", "different approach", etc.
     *
     * @return true if user wants improved results
     */
    boolean hasNegativeFeedback();

    /**
     * Get the last execution result for a specific tool.
     *
     * @param toolName Name of the tool
     * @return Last execution result or null
     */
    Object getLastToolResult(String toolName);
}
