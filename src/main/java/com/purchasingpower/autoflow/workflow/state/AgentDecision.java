package com.purchasingpower.autoflow.workflow.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Decision made by an agent about what to do next.
 * Used by LangGraph4J for conditional routing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDecision implements Serializable {

    /**
     * Where to go next
     */
    private NextStep nextStep;

    /**
     * Message to show user (if ASK_DEV)
     */
    private String message;

    /**
     * Confidence in this decision (0.0 - 1.0)
     */
    private double confidence;

    /**
     * Reason for this decision (for logging/debugging)
     */
    private String reasoning;

    public enum NextStep {
        PROCEED,      // Continue to next agent
        ASK_DEV,      // Pause and wait for user input
        ERROR,        // Fatal error, abort workflow
        RETRY,        // Retry this agent (e.g., after user provides more info)
        END_SUCCESS,  // Workflow complete successfully
        END_FAILURE   // Workflow failed, cannot continue
    }

    // Convenience factory methods

    public static AgentDecision proceed(String reasoning) {
        return AgentDecision.builder()
                .nextStep(NextStep.PROCEED)
                .reasoning(reasoning)
                .confidence(1.0)
                .build();
    }

    public static AgentDecision askDev(String message) {
        return AgentDecision.builder()
                .nextStep(NextStep.ASK_DEV)
                .message(message)
                .confidence(0.0)
                .build();
    }

    public static AgentDecision error(String message) {
        return AgentDecision.builder()
                .nextStep(NextStep.ERROR)
                .message(message)
                .reasoning("Fatal error occurred")
                .confidence(0.0)
                .build();
    }

    public static AgentDecision retry(String reasoning) {
        return AgentDecision.builder()
                .nextStep(NextStep.RETRY)
                .reasoning(reasoning)
                .build();
    }

    public static AgentDecision endSuccess(String message) {
        return AgentDecision.builder()
                .nextStep(NextStep.END_SUCCESS)
                .message(message)
                .confidence(1.0)
                .build();
    }
}