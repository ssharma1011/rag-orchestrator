package com.purchasingpower.autoflow.repository;

import com.purchasingpower.autoflow.model.ast.DependencyEdge;
import com.purchasingpower.autoflow.model.graph.GraphEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA Repository for GraphEdge entities.
 * Provides graph traversal queries using recursive SQL.
 */
@Repository
public interface GraphEdgeRepository extends JpaRepository<GraphEdge, Long> {

    /**
     * Find all outgoing edges from a node (direct dependencies).
     */
    List<GraphEdge> findBySourceNodeId(String sourceNodeId);

    /**
     * Find all incoming edges to a node (direct dependents).
     */
    List<GraphEdge> findByTargetNodeId(String targetNodeId);

    /**
     * Find edges by relationship type.
     */
    List<GraphEdge> findBySourceNodeIdAndRelationshipType(
        String sourceNodeId,
        DependencyEdge.RelationshipType type
    );

    /**
     * Find all edges in a repository.
     */
    List<GraphEdge> findByRepoName(String repoName);

    /**
     * Delete all edges for a repository.
     */
    void deleteByRepoName(String repoName);

    /**
     * Find all transitive dependencies (recursive query).
     * Uses Oracle CONNECT BY or SQL standard WITH RECURSIVE.
     */
    @Query(value = """
        WITH RECURSIVE deps AS (
            SELECT target_node_id, 1 as depth, source_node_id as root_node
            FROM code_edges
            WHERE source_node_id = :startNode
              AND repo_name = :repoName
            
            UNION ALL
            
            SELECT e.target_node_id, d.depth + 1, d.root_node
            FROM code_edges e
            INNER JOIN deps d ON e.source_node_id = d.target_node_id
            WHERE d.depth < :maxDepth
              AND e.repo_name = :repoName
        )
        SELECT DISTINCT target_node_id FROM deps
        """, nativeQuery = true)
    List<String> findTransitiveDependencies(
        @Param("startNode") String startNode,
        @Param("repoName") String repoName,
        @Param("maxDepth") int maxDepth
    );

    /**
     * Find all transitive dependents (who depends on this node?).
     */
    @Query(value = """
        WITH RECURSIVE dependents AS (
            SELECT source_node_id, 1 as depth, target_node_id as root_node
            FROM code_edges
            WHERE target_node_id = :targetNode
              AND repo_name = :repoName
            
            UNION ALL
            
            SELECT e.source_node_id, d.depth + 1, d.root_node
            FROM code_edges e
            INNER JOIN dependents d ON e.target_node_id = d.source_node_id
            WHERE d.depth < :maxDepth
              AND e.repo_name = :repoName
        )
        SELECT DISTINCT source_node_id FROM dependents
        """, nativeQuery = true)
    List<String> findTransitiveDependents(
        @Param("targetNode") String targetNode,
        @Param("repoName") String repoName,
        @Param("maxDepth") int maxDepth
    );

    /**
     * Find shortest path between two nodes using breadth-first search.
     */
    @Query(value = """
        WITH RECURSIVE path AS (
            SELECT 
                target_node_id as node_id,
                CAST(source_node_id || '->' || target_node_id AS VARCHAR(4000)) as path_str,
                1 as depth
            FROM code_edges
            WHERE source_node_id = :startNode
              AND repo_name = :repoName
            
            UNION ALL
            
            SELECT 
                e.target_node_id,
                CAST(p.path_str || '->' || e.target_node_id AS VARCHAR(4000)),
                p.depth + 1
            FROM code_edges e
            INNER JOIN path p ON e.source_node_id = p.node_id
            WHERE p.depth < :maxDepth
              AND e.repo_name = :repoName
              AND p.path_str NOT LIKE '%' || e.target_node_id || '%'
        )
        SELECT path_str
        FROM path
        WHERE node_id = :endNode
        ORDER BY depth
        FETCH FIRST 1 ROW ONLY
        """, nativeQuery = true)
    String findShortestPath(
        @Param("startNode") String startNode,
        @Param("endNode") String endNode,
        @Param("repoName") String repoName,
        @Param("maxDepth") int maxDepth
    );

    /**
     * Count total edges in a repository.
     */
    long countByRepoName(String repoName);
}
