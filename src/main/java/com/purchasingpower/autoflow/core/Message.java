package com.purchasingpower.autoflow.core;

import java.time.LocalDateTime;

/**
 * Single message in a conversation.
 *
 * @since 2.0.0
 */
public interface Message {
    String getId();
    String getRole();
    String getContent();
    LocalDateTime getTimestamp();
}
