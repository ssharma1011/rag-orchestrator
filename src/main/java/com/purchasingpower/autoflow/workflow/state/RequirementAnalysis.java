package com.purchasingpower.autoflow.workflow.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of analyzing user's requirement with LLM.
 * Used by RequirementAnalyzerAgent.
 * CRITICAL FIX: Ensure all Lists are never null for Jackson serialization
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RequirementAnalysis implements Serializable {

    /**
     * Type of task: "bug_fix", "feature", "refactor", "test", "explanation", "documentation"
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
    private List<String> keyVerbs;

    /**
     * Questions agent needs answered before proceeding
     * Example: "Should retry be exponential or linear backoff?"
     */
    private List<String> questions;

    /**
     * Confidence score (0.0 - 1.0)
     * < 0.7 → Ask dev for clarification
     */
    private double confidence;

    // ================================================================
    // CUSTOM GETTERS - GUARANTEE NON-NULL LISTS
    // ================================================================

    public List<String> getKeyVerbs() {
        if (keyVerbs == null) {
            keyVerbs = new ArrayList<>();
        }
        return keyVerbs;
    }

    public List<String> getQuestions() {
        if (questions == null) {
            questions = new ArrayList<>();
        }
        return questions;
    }

    /**
     * Builder customization to ensure default values
     */
    public static class RequirementAnalysisBuilder {
        public RequirementAnalysis build() {
            if (keyVerbs == null) keyVerbs = new ArrayList<>();
            if (questions == null) questions = new ArrayList<>();

            return new RequirementAnalysis(
                    taskType,
                    domain,
                    summary,
                    detailedDescription,
                    keyVerbs,
                    questions,
                    confidence
            );
        }
    }
}