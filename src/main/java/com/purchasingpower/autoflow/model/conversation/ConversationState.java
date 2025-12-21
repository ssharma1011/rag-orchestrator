package com.purchasingpower.autoflow.model.conversation;

/**
 * Represents the state of a conversation in the workflow.
 * Used to track the progress of a conversation through the AutoFlow workflow.
 */

public enum ConversationState {
    /**
     * Conversation initialized but not started
     */
    PENDING,

    /**
     * Conversation is actively being processed\
     */
    IN_PROGRESS,

    /**
     * Conversation paused, waiting for user input
     */
    PAUSED,

    /**
     * Conversation completed successfully
     */
    COMPLETED,

    /**
     * Conversation failed due to an error
     */
    FAILED,

    /**
     * Conversation cancelled by user
     */
    CANCELLED

}