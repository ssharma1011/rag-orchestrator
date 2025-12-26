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
    // CAPABILITY-BASED ROUTING FIELDS
    // ================================================================

    /**
     * Data sources needed to answer this query.
     * Examples: ["code"], ["confluence"], ["code", "confluence"]
     * Future: ["jira"], ["slack"], etc.
     */
    @Builder.Default
    private List<String> dataSources = new ArrayList<>();

    /**
     * Does this task modify code?
     * true = needs PR creation, approval, testing
     * false = read-only query
     */
    @Builder.Default
    private boolean modifiesCode = false;

    /**
     * Does this need user approval before proceeding?
     * true = pause for approval (e.g., large refactor)
     * false = proceed automatically (e.g., simple bug fix)
     */
    @Builder.Default
    private boolean needsApproval = false;

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

    public List<String> getDataSources() {
        if (dataSources == null) {
            dataSources = new ArrayList<>();
        }
        return dataSources;
    }

    // ================================================================
    // ROUTING HELPER METHODS
    // ================================================================

    /**
     * Is this a read-only query (no code changes)?
     */
    public boolean isReadOnly() {
        return !modifiesCode;
    }

    /**
     * Does this need code context from Neo4j/indexing?
     */
    public boolean needsCodeContext() {
        return getDataSources().contains("code");
    }

    /**
     * Does this need Confluence documentation?
     */
    public boolean needsConfluenceContext() {
        return getDataSources().contains("confluence");
    }

    /**
     * Is this just casual chat (no data sources needed)?
     */
    public boolean isCasualChat() {
        return "chat".equalsIgnoreCase(taskType) || getDataSources().isEmpty();
    }
}