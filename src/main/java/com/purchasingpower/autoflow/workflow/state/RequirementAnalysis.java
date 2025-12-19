package com.purchasingpower.autoflow.workflow.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of analyzing user's requirement with LLM.
 * Used by RequirementAnalyzerAgent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequirementAnalysis {

    /**
     * Type of task: "bug_fix", "feature", "refactor", "test"
     */
    private String taskType;

    /**
     * Domain extracted from requirement
     * Examples: "payment", "user", "order", "checkout"
     */
    private String domain;

    /**
     * One-sentence summary
     */
    private String summary;

    /**
     * Detailed breakdown of what needs to be done
     */
    private String detailedDescription;

    /**
     * Key action verbs extracted (helps with scope discovery)
     * Examples: ["add", "retry"], ["fix", "null check"], ["refactor", "extract"]
     */
    @Builder.Default
    private List<String> keyVerbs = new ArrayList<>();

    /**
     * Questions agent needs answered before proceeding
     * Example: "Should retry be exponential or linear backoff?"
     */
    @Builder.Default
    private List<String> questions = new ArrayList<>();

    /**
     * Confidence score (0.0 - 1.0)
     * < 0.7 → Ask dev for clarification
     */
    private double confidence;
}