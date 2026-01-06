package com.purchasingpower.autoflow.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request for chat endpoint.
 *
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /**
     * The user's message/question.
     */
    private String message;

    /**
     * Existing conversation ID (null for new conversation).
     */
    private String conversationId;

    /**
     * User ID.
     */
    private String userId;

    /**
     * Repository URL to chat about.
     * If provided, the repo will be auto-indexed if not already.
     * Required for first message in a conversation.
     */
    private String repoUrl;

    /**
     * Branch to use (default: main).
     */
    private String branch;

    /**
     * Conversation mode: EXPLORE, DEBUG, IMPLEMENT, REVIEW.
     * Determines how the agent behaves.
     */
    private String mode;

    /**
     * Optional metadata for extensibility.
     * Can include: targetFile, jiraTicket, prNumber, etc.
     */
    private Map<String, String> metadata;
}
