package com.purchasingpower.autoflow.model.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Structured approval result from LLM natural language understanding.
 *
 * Represents the LLM's interpretation of a user's response to a
 * scope proposal, including approval status, type, confidence,
 * and any requested modifications.
 *
 * @see com.purchasingpower.autoflow.workflow.agents.ScopeApprovalAgent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalResult {
    /**
     * Whether the proposal was approved.
     */
    boolean approved;

    /**
     * Type of approval: "full", "partial", or "rejected".
     */
    String approvalType;

    /**
     * LLM's confidence in the interpretation (0.0 to 1.0).
     */
    double confidence;

    /**
     * Modified scope if partial approval.
     */
    Map<String, Object> modifiedScope;
}
