package com.purchasingpower.autoflow.core;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a stateful conversation with the agent.
 *
 * <p>Maintains context across multiple turns, enabling follow-up questions
 * and referential language ("they", "it", "the method I just asked about").
 *
 * @since 2.0.0
 */
public interface Conversation {

    /**
     * Unique identifier for this conversation.
     */
    String getId();

    /**
     * User who initiated this conversation.
     */
    String getUserId();

    /**
     * Repository context for this conversation.
     * Can be a single repo or multiple repos.
     */
    List<String> getRepositoryIds();

    /**
     * All messages in this conversation.
     */
    List<Message> getMessages();

    /**
     * Current conversation state.
     */
    ConversationState getState();

    /**
     * When this conversation was created.
     */
    LocalDateTime getCreatedAt();

    /**
     * When this conversation was last updated.
     */
    LocalDateTime getUpdatedAt();

    /**
     * Add a message to this conversation.
     *
     * @param role Message role (user, assistant, system)
     * @param content Message content
     * @return The created message
     */
    Message addMessage(String role, String content);

    /**
     * Get conversation memory (summarized context for LLM).
     *
     * @return Context string for LLM prompts
     */
    String getMemoryContext();

    /**
     * Single message in a conversation.
     */
    interface Message {
        String getId();
        String getRole();
        String getContent();
        LocalDateTime getTimestamp();
    }

    /**
     * Conversation states.
     */
    enum ConversationState {
        /**
         * Conversation is active and accepting messages.
         */
        ACTIVE,

        /**
         * Waiting for user response to a question.
         */
        WAITING_FOR_USER,

        /**
         * Agent is processing a request.
         */
        PROCESSING,

        /**
         * Conversation is completed.
         */
        COMPLETED,

        /**
         * Conversation encountered an error.
         */
        ERROR
    }
}
