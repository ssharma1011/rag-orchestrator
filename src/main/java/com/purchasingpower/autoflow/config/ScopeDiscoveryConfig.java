package com.purchasingpower.autoflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the Scope Discovery Agent.
 *
 * <p>Manages limits and thresholds for discovering relevant code within a repository,
 * including constraints on file count, class analysis depth, and vector similarity
 * matching for identifying related code segments.
 *
 * <p>Properties are loaded from the {@code app.agents.scope-discovery} namespace in
 * application.yml. Example configuration:
 * <pre>
 * app:
 *   agents:
 *     scope-discovery:
 *       max-files: 7
 *       max-classes: 3
 *       max-chunks: 10
 *       max-dependencies: 5
 *       default-architecture: "Spring Boot MVC"
 *       similarity:
 *         min-threshold: 0.5
 *         max-threshold: 0.8
 *         min-matches: 3
 *         significant-gap: 0.15
 *         top-score-multiplier: 0.7
 * </pre>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe as Spring manages a single instance
 * and all fields are effectively immutable after initialization.
 *
 * @author AutoFlow Pipeline
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "app.agents.scope-discovery")
@Data
public class ScopeDiscoveryConfig {

    /**
     * Maximum number of source files to analyze during scope discovery.
     * Prevents processing of massive repositories that could timeout or consume excessive resources.
     * Default: 7
     */
    private int maxFiles;

    /**
     * Maximum number of classes to extract and analyze from discovered files.
     * Limits the breadth of class analysis to prevent overwhelming the model.
     * Default: 3
     */
    private int maxClasses;

    /**
     * Maximum number of code chunks to create during vectorization for similarity matching.
     * Affects how granularly the code is broken down for embedding.
     * Default: 10
     */
    private int maxChunks;

    /**
     * Maximum number of dependencies to analyze when building the code dependency graph.
     * Controls scope of inter-module relationship discovery.
     * Default: 5
     */
    private int maxDependencies;

    /**
     * Default architecture pattern to assume if not explicitly specified.
     * Used as a fallback for prompt context when analyzing scope.
     * Default: "Spring Boot MVC"
     */
    private String defaultArchitecture;

    /**
     * Configuration for vector similarity matching used to find related code.
     * These thresholds control which code segments are considered "related" based on
     * vector embedding similarity.
     */
    private SimilarityConfig similarity;

    /**
     * Configuration for vector similarity matching thresholds.
     *
     * <p>Used by the scope discovery agent when querying Pinecone for related code
     * segments based on semantic similarity of embeddings.
     */
    @Data
    public static class SimilarityConfig {

        /**
         * Minimum similarity score (0.0-1.0) to include a result.
         * Results below this threshold are discarded.
         * Default: 0.5
         */
        private double minThreshold;

        /**
         * Maximum similarity score (0.0-1.0) to consider a different match.
         * Results above this are considered the same concept, potentially filtered.
         * Default: 0.8
         */
        private double maxThreshold;

        /**
         * Minimum number of matching results required for confidence in scope discovery.
         * If fewer than this many results meet the threshold, additional analysis is needed.
         * Default: 3
         */
        private int minMatches;

        /**
         * Significant gap threshold (0.0-1.0) between consecutive similarity scores.
         * If a gap larger than this appears, it indicates distinct groups of related code.
         * Default: 0.15
         */
        private double significantGap;

        /**
         * Multiplier applied to the top similarity score when determining secondary threshold.
         * Used for dynamic threshold adjustment based on best match quality.
         * Range: 0.0-1.0. Example: topScore=0.85, multiplier=0.7 gives threshold=0.595
         * Default: 0.7
         */
        private double topScoreMultiplier;
    }
}
