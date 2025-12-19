package com.purchasingpower.autoflow.workflow.state;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single message in conversation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String role;      // "USER", "ASSISTANT", "SYSTEM"
    private String content;
    private long timestamp;
}