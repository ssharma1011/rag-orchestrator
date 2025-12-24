package com.purchasingpower.autoflow.model.dto;

import com.purchasingpower.autoflow.workflow.state.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for conversation history.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowHistoryResponse {
    private String conversationId;
    private List<ChatMessage> messages;
    private String status;
}
