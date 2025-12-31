package com.purchasingpower.autoflow.model.retrieval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RetrievalStrategy - Single strategy for retrieving code.
 *
 * Supported strategy types:
 * - metadata_filter: Filter by annotations, class names, file paths
 * - semantic_search: Embedding-based similarity search
 * - graph_traversal: Follow dependencies in Neo4j
 * - file_pattern: Regex match on file paths (README, pom.xml, etc)
 * - cross_repo_api: Find API contracts between services
 * - broad_overview: Get high-level project structure (README + main classes)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalStrategy implements Serializable {

    /**
     * Strategy type.
     * Values: metadata_filter, semantic_search, graph_traversal, file_pattern, cross_repo_api, broad_overview
     */
    private String type;

    /**
     * Strategy-specific parameters.
     *
     * Examples:
     * - metadata_filter: {"annotations": "@RestController,@Service", "className_contains": "Payment"}
     * - semantic_search: {"query": "payment processing logic", "top_k": 10}
     * - graph_traversal: {"start_node": "PaymentService", "relationship": "DEPENDS_ON", "max_depth": 3}
     * - file_pattern: {"pattern": "README\\.md|pom\\.xml"}
     * - cross_repo_api: {"api_type": "REST"}
     */
    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();

    /**
     * Target repositories for this strategy.
     * Empty list means current repo only.
     */
    @Builder.Default
    private List<String> targetRepos = new ArrayList<>();

    /**
     * LLM's reasoning for why this strategy is needed.
     */
    private String reasoning;

    /**
     * Priority/order hint (lower = higher priority).
     * Default: strategies execute in order they appear in plan.
     */
    @Builder.Default
    private Integer priority = 0;

    /**
     * Maximum number of results to retrieve for this strategy.
     * Default: strategy-specific defaults.
     */
    private Integer maxResults;

    /**
     * Get a parameter value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, T defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return (T) value;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    /**
     * Get a parameter value (throws if missing).
     */
    @SuppressWarnings("unchecked")
    public <T> T getRequiredParameter(String key) {
        Object value = parameters.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return (T) value;
    }

    /**
     * Check if this strategy targets a specific repo.
     */
    public boolean targetsRepo(String repoName) {
        return targetRepos.isEmpty() || targetRepos.contains(repoName);
    }
}
