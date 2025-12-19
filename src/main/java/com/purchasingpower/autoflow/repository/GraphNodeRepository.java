package com.purchasingpower.autoflow.repository;

import com.purchasingpower.autoflow.model.ast.ChunkType;
import com.purchasingpower.autoflow.model.graph.GraphNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA Repository for GraphNode entities.
 * Provides basic CRUD and custom queries for graph nodes.
 */
@Repository
public interface GraphNodeRepository extends JpaRepository<GraphNode, String> {

    /**
     * Find all nodes for a specific repository.
     */
    List<GraphNode> findByRepoName(String repoName);

    /**
     * Find a node by its fully qualified name.
     */
    Optional<GraphNode> findByFullyQualifiedName(String fqn);

    /**
     * Find all nodes of a specific type in a repository.
     */
    List<GraphNode> findByRepoNameAndType(String repoName, ChunkType type);

    /**
     * Find nodes by package prefix (for namespace queries).
     */
    @Query("SELECT n FROM GraphNode n WHERE n.repoName = :repoName AND n.packageName LIKE :packagePrefix%")
    List<GraphNode> findByRepoNameAndPackagePrefix(
        @Param("repoName") String repoName,
        @Param("packagePrefix") String packagePrefix
    );

    /**
     * Delete all nodes for a repository (cleanup before reindex).
     */
    void deleteByRepoName(String repoName);

    /**
     * Count nodes by type for statistics.
     */
    long countByRepoNameAndType(String repoName, ChunkType type);
}
