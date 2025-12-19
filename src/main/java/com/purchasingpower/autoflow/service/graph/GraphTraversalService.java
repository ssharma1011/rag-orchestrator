package com.purchasingpower.autoflow.service.graph;

import com.purchasingpower.autoflow.model.graph.ImpactAnalysisReport;
import java.util.List;

/**
 * Service for traversing the code dependency graph.
 * Supports all graph query patterns needed for RAG context expansion.
 */
public interface GraphTraversalService {

    /**
     * Find direct dependencies (depth=1) of a class/method.
     * 
     * @param nodeId Fully qualified node ID (e.g., "payment-service:PaymentService")
     * @param repoName Repository name for filtering
     * @return List of directly dependent node IDs
     */
    List<String> findDirectDependencies(String nodeId, String repoName);

    /**
     * Find all transitive dependencies up to maxDepth.
     * Uses recursive SQL for efficient traversal.
     * 
     * @param nodeId Starting node
     * @param repoName Repository name
     * @param maxDepth Maximum traversal depth (typically 2-3)
     * @return List of all dependent node IDs (transitive closure)
     */
    List<String> findAllDependencies(String nodeId, String repoName, int maxDepth);

    /**
     * Find who depends on this node (reverse dependencies).
     * 
     * @param nodeId Target node
     * @param repoName Repository name
     * @return List of node IDs that depend on this node
     */
    List<String> findDirectDependents(String nodeId, String repoName);

    /**
     * Find all nodes that transitively depend on this node.
     * 
     * @param nodeId Target node
     * @param repoName Repository name
     * @param maxDepth Maximum traversal depth
     * @return List of all dependent node IDs
     */
    List<String> findAllDependents(String nodeId, String repoName, int maxDepth);

    /**
     * Find shortest path between two nodes.
     * Returns null if no path exists.
     * 
     * @param startNode Starting node
     * @param endNode Target node
     * @param repoName Repository name
     * @param maxDepth Maximum path length to search
     * @return Path as string (e.g., "A->B->C") or null
     */
    String findShortestPath(String startNode, String endNode, String repoName, int maxDepth);

    /**
     * Find all nodes with a specific role tag.
     * Useful for queries like "find all Kafka consumers".
     * 
     * @param role Role tag (e.g., "spring-kafka:consumer")
     * @param repoName Repository name
     * @return List of node IDs with this role
     */
    List<String> findByRole(String role, String repoName);

    /**
     * Analyze impact of changing a node.
     * Returns comprehensive report of affected components.
     * 
     * @param nodeId Node to analyze
     * @param repoName Repository name
     * @return Impact analysis report with metrics
     */
    ImpactAnalysisReport analyzeImpact(String nodeId, String repoName);

    /**
     * Get full context for RAG: node + dependencies + dependents.
     * This is the main method used by EnhancedRagLlmService.
     * 
     * @param nodeId Central node
     * @param repoName Repository name
     * @param dependencyDepth How deep to traverse dependencies (typically 2)
     * @param dependentDepth How deep to traverse dependents (typically 1)
     * @return List of all relevant node IDs for context
     */
    List<String> getFullContext(String nodeId, String repoName, int dependencyDepth, int dependentDepth);
}
