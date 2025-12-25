package com.purchasingpower.autoflow.model.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.workflow.state.AgentDecision;
import com.purchasingpower.autoflow.workflow.state.WorkflowState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Response DTO for workflow status.
 * 
 * CRITICAL FIX: Handles LinkedHashMap deserialization issue when loading
 * WorkflowState from database (ObjectMapper deserializes nested objects as Maps).
 */
@Slf4j
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

    private static final ObjectMapper mapper = new ObjectMapper();

    public static WorkflowResponse fromState(WorkflowState state) {
        try {
            // Extract AgentDecision safely (handles LinkedHashMap case)
            AgentDecision decision = extractAgentDecision(state);
            
            return WorkflowResponse.builder()
                    .success(true)
                    .conversationId(state.getConversationId())
                    .status(state.getWorkflowStatus())
                    .currentAgent(state.getCurrentAgent())
                    .message(decision != null ? decision.getMessage() : null)
                    .awaitingUserInput(decision != null && 
                            decision.getNextStep() == AgentDecision.NextStep.ASK_DEV)
                    .progress(calculateProgress(state))
                    .build();
        } catch (Exception e) {
            log.error("Error building WorkflowResponse from state", e);
            // Fallback: return basic response without decision details
            return WorkflowResponse.builder()
                    .success(true)
                    .conversationId(state.getConversationId())
                    .status(state.getWorkflowStatus())
                    .currentAgent(state.getCurrentAgent())
                    .message("Workflow in progress")
                    .awaitingUserInput(false)
                    .progress(calculateProgress(state))
                    .build();
        }
    }

    /**
     * Safely extract AgentDecision from state.
     * 
     * HANDLES: When ObjectMapper deserializes state from DB, nested objects
     * become LinkedHashMaps instead of their proper types.
     */
    private static AgentDecision extractAgentDecision(WorkflowState state) {
        Object decisionObj = state.getLastAgentDecision();
        
        if (decisionObj == null) {
            return null;
        }
        
        // If it's already an AgentDecision, return it
        if (decisionObj instanceof AgentDecision) {
            return (AgentDecision) decisionObj;
        }
        
        // If it's a LinkedHashMap (from JSON deserialization), convert it
        if (decisionObj instanceof java.util.Map) {
            try {
                return mapper.convertValue(decisionObj, AgentDecision.class);
            } catch (Exception e) {
                log.warn("Failed to convert Map to AgentDecision: {}", e.getMessage());
                return null;
            }
        }
        
        // Unknown type, return null
        log.warn("Unknown AgentDecision type: {}", decisionObj.getClass());
        return null;
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
