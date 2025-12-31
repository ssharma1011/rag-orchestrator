package com.purchasingpower.autoflow.repository;

import com.purchasingpower.autoflow.model.graph.GraphNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * UPDATED: Repository for querying code nodes with knowledge graph support.
 *
 * NEW METHODS:
 * - findByRepoNameAndDomain() - Domain-based search for ScopeDiscoveryAgent
 * - findByRepoNameAndBusinessCapability() - Capability-based search
 * - findByRepoNameAndFeaturesContaining() - Feature-based search
 * - findByRepoNameAndConceptsContaining() - Concept-based search
 *
 * These enable the multi-strategy search in ScopeDiscoveryAgent!
 */
@Repository
public interface GraphNodeRepository extends JpaRepository<GraphNode, Long> {

    // ================================================================
    // EXISTING METHODS (From original implementation)
    // ================================================================

    /**
     * Find node by its unique ID.
     */
    Optional<GraphNode> findByNodeId(String nodeId);

    /**
     * Find all nodes in a repository.
     */
    List<GraphNode> findByRepoName(String repoName);

    /**
     * Find nodes by type (CLASS, METHOD, INTERFACE, etc.).
     */
    List<GraphNode> findByRepoNameAndType(String repoName, String type);

    /**
     * Find node by fully qualified name.
     */
    Optional<GraphNode> findByRepoNameAndFullyQualifiedName(String repoName, String fqn);

    /**
     * Find nodes in a package.
     */
    List<GraphNode> findByRepoNameAndPackageName(String repoName, String packageName);

    /**
     * Find nodes in a file.
     */
    List<GraphNode> findByRepoNameAndFilePath(String repoName, String filePath);

    /**
     * Delete all nodes for a repository (for re-indexing).
     *
     * IMPORTANT: Uses bulk DELETE query for performance and atomicity.
     * Derived delete methods (deleteByRepoName) execute SELECT + N individual DELETEs (slow!).
     * This custom query executes a single bulk DELETE (fast!).
     *
     * @Modifying tells Spring this modifies data (not a SELECT)
     * @Transactional ensures atomicity with surrounding operations
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM GraphNode n WHERE n.repoName = :repoName")
    void deleteByRepoName(@Param("repoName") String repoName);

    /**
     * Count total nodes in a repository.
     */
    long countByRepoName(String repoName);

    /**
     * Search nodes by simple name (case-insensitive).
     */
    @Query("SELECT n FROM GraphNode n WHERE n.repoName = :repoName AND LOWER(n.simpleName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<GraphNode> searchByName(@Param("repoName") String repoName, @Param("name") String name);

    // ================================================================
    // NEW METHODS: Knowledge Graph Support
    // ================================================================

    /**
     * Find all nodes in a specific business domain.
     *
     * CRITICAL for ScopeDiscoveryAgent Strategy #1 (Domain Search)!
     *
     * Example: findByRepoNameAndDomain("my-repo", "payment")
     *   Returns: PaymentService, PaymentProcessor, PaymentValidator, etc.
     *
     * @param repoName Repository name
     * @param domain Business domain (e.g., "payment", "user", "order")
     * @return List of nodes in this domain
     */
    List<GraphNode> findByRepoNameAndDomain(String repoName, String domain);

    /**
     * Find all nodes with a specific business capability.
     *
     * Example: findByRepoNameAndBusinessCapability("my-repo", "payment-processing")
     *   Returns: All classes involved in payment processing
     *
     * @param repoName Repository name
     * @param businessCapability Business capability
     * @return List of nodes with this capability
     */
    List<GraphNode> findByRepoNameAndBusinessCapability(String repoName, String businessCapability);

    /**
     * Find all nodes in a domain that also match a specific type.
     *
     * Example: Find all SERVICE classes in payment domain
     *
     * @param repoName Repository name
     * @param domain Business domain
     * @param type Node type (CLASS, METHOD, etc.)
     * @return List of matching nodes
     */
    @Query("SELECT n FROM GraphNode n WHERE n.repoName = :repoName AND n.domain = :domain AND n.type = :type")
    List<GraphNode> findByRepoNameAndDomainAndType(
            @Param("repoName") String repoName,
            @Param("domain") String domain,
            @Param("type") String type
    );


    /**
     * Get all unique business capabilities in a repository.
     *
     * @param repoName Repository name
     * @return List of unique business capabilities
     */
    @Query("SELECT DISTINCT n.businessCapability FROM GraphNode n WHERE n.repoName = :repoName AND n.businessCapability IS NOT NULL ORDER BY n.businessCapability")
    List<String> findDistinctBusinessCapabilitiesByRepoName(@Param("repoName") String repoName);

    /**
     * Complex domain search with multiple criteria.
     *
     * Used by ScopeDiscoveryAgent for multi-faceted search.
     *
     * Example: Find all SERVICE or CONTROLLER classes in payment domain
     *
     * @param repoName Repository name
     * @param domain Business domain
     * @param types List of node types to match
     * @return List of matching nodes
     */
    @Query("SELECT n FROM GraphNode n WHERE n.repoName = :repoName AND n.domain = :domain AND n.type IN :types")
    List<GraphNode> findByRepoNameAndDomainAndTypeIn(
            @Param("repoName") String repoName,
            @Param("domain") String domain,
            @Param("types") List<String> types
    );

    /**
     * Get statistics about knowledge graph coverage.
     *
     * Returns map: { totalNodes, nodesWithDomain, nodesWithCapability, ... }
     *
     * @param repoName Repository name
     * @return Knowledge graph coverage statistics
     */
    @Query("""
        SELECT 
            COUNT(*) as totalNodes,
            SUM(CASE WHEN n.domain IS NOT NULL THEN 1 ELSE 0 END) as nodesWithDomain,
            SUM(CASE WHEN n.businessCapability IS NOT NULL THEN 1 ELSE 0 END) as nodesWithCapability,
            SUM(CASE WHEN n.features IS NOT NULL THEN 1 ELSE 0 END) as nodesWithFeatures,
            SUM(CASE WHEN n.concepts IS NOT NULL THEN 1 ELSE 0 END) as nodesWithConcepts
        FROM GraphNode n 
        WHERE n.repoName = :repoName
        """)
    Object getKnowledgeGraphStats(@Param("repoName") String repoName);


    /**
     * Find nodes that implement a specific feature.
     *
     * Uses LIKE query since features is comma-separated string.
     *
     * Example: "retry" finds all classes with retry feature
     *
     * Add this to GraphNodeRepository:
     */
    @Query("SELECT n FROM GraphNode n WHERE n.repoName = :repoName AND n.features LIKE CONCAT('%', :feature, '%')")
    List<GraphNode> findByRepoNameAndFeaturesContaining(
            @Param("repoName") String repoName,
            @Param("feature") String feature
    );

    /**
     * Find nodes related to a specific business concept.
     *
     * Example: "transaction" finds all classes dealing with transactions
     *
     * Add this to GraphNodeRepository:
     */
    @Query("SELECT n FROM GraphNode n WHERE n.repoName = :repoName AND n.concepts LIKE CONCAT('%', :concept, '%')")
    List<GraphNode> findByRepoNameAndConceptsContaining(
            @Param("repoName") String repoName,
            @Param("concept") String concept
    );

    /**
     * Get all unique domains in a repository.
     *
     * Useful for analytics and UI dropdowns.
     *
     * Add this to GraphNodeRepository:
     */
    @Query("SELECT DISTINCT n.domain FROM GraphNode n WHERE n.repoName = :repoName AND n.domain IS NOT NULL ORDER BY n.domain")
    List<String> findDistinctDomainsByRepoName(@Param("repoName") String repoName);
}
