package com.purchasingpower.autoflow.core;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a stateful conversation with the agent.
 *
 * @since 2.0.0
 */
public interface Conversation {

    String getId();

    String getUserId();

    List<String> getRepositoryIds();

    List<Message> getMessages();

    ConversationState getState();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();

    Message addMessage(String role, String content);

    String getMemoryContext();
}
