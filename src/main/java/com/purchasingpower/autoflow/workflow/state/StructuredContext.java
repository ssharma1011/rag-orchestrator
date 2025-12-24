package com.purchasingpower.autoflow.workflow.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete context for code generation.
 * Built with 100% certainty - no fuzzy matching.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuredContext implements Serializable {

    /**
     * Context for each file in scope
     */
    @Builder.Default
    private Map<String, FileContext> fileContexts = new HashMap<>();

    /**
     * Domain-level context (business rules, patterns)
     */
    private DomainContext domainContext;

    /**
     * Overall confidence (0.0 - 1.0)
     */
    private double confidence;

    /**
     * Context for a single file
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileContext {
        /**
         * Full file path
         */
        private String filePath;

        /**
         * Full source code (for modify)
         */
        private String currentCode;

        /**
         * What this class does (brief summary)
         */
        private String purpose;

        /**
         * Classes this file depends on (imports)
         */
        private List<String> dependencies;

        /**
         * Classes that depend on this file
         */
        private List<String> dependents;

        /**
         * Existing test coverage
         */
        private List<String> coveredByTests;
    }

    /**
     * Domain-level business context
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DomainContext {
        /**
         * Domain name (e.g., "payment")
         */
        private String domain;

        /**
         * Business rules from knowledge graph
         */
        private List<String> businessRules;

        /**
         * Related concepts
         */
        private List<String> concepts;

        /**
         * Architecture pattern used
         */
        private String architecturePattern;

        /**
         * All classes in this domain
         */
        private List<String> domainClasses;
    }
}