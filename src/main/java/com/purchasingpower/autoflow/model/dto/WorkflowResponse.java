package com.purchasingpower.autoflow.model.dto;

import com.purchasingpower.autoflow.workflow.state.AgentDecision;
import com.purchasingpower.autoflow.workflow.state.WorkflowState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for workflow status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowResponse {
    private boolean success;
    private String conversationId;
    private String status;
    private String currentAgent;
    private String message;
    private boolean awaitingUserInput;
    private int progress;
    private String error;

    public static WorkflowResponse fromState(WorkflowState state) {
        return WorkflowResponse.builder()
                .success(true)
                .conversationId(state.getConversationId())
                .status(state.getWorkflowStatus())
                .currentAgent(state.getCurrentAgent())
                .message(state.getLastAgentDecision() != null ? 
                        state.getLastAgentDecision().getMessage() : null)
                .awaitingUserInput(state.getLastAgentDecision() != null && 
                        state.getLastAgentDecision().getNextStep() == AgentDecision.NextStep.ASK_DEV)
                .progress(calculateProgress(state))
                .build();
    }

    public static WorkflowResponse error(String errorMessage) {
        return WorkflowResponse.builder()
                .success(false)
                .error(errorMessage)
                .build();
    }

    private static int calculateProgress(WorkflowState state) {
        String agent = state.getCurrentAgent();
        if (agent == null) return 0;
        
        return switch (agent) {
            case "requirement_analyzer" -> 5;
            case "log_analyzer" -> 10;
            case "code_indexer" -> 20;
            case "scope_discovery" -> 35;
            case "context_builder" -> 50;
            case "code_generator" -> 65;
            case "build_validator" -> 75;
            case "test_runner" -> 85;
            case "pr_reviewer" -> 90;
            case "readme_generator" -> 95;
            case "pr_creator" -> 100;
            default -> 0;
        };
    }
}
