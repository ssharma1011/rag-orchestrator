package com.purchasingpower.autoflow.search;

import com.purchasingpower.autoflow.core.SearchResult;

import java.util.List;
import java.util.Map;

/**
 * Unified search service orchestrating all search modes.
 *
 * <p>Provides a single interface for searching the codebase using:
 * <ul>
 *   <li>Structural queries - Graph-based relationship queries</li>
 *   <li>Semantic search - Vector similarity for natural language</li>
 *   <li>Temporal search - Git history analysis</li>
 *   <li>Hybrid search - Combined approach for best results</li>
 * </ul>
 *
 * @since 2.0.0
 */
public interface SearchService {

    /**
     * Search across repositories using natural language.
     *
     * <p>Automatically selects the best search strategy based on the query.
     *
     * @param query Natural language query
     * @param options Search options
     * @return Search results
     */
    List<SearchResult> search(String query, SearchOptions options);

    /**
     * Execute a structural (graph) query.
     *
     * @param query Cypher query or structured query object
     * @param repositoryIds Repositories to search in
     * @return Matching entities
     */
    List<SearchResult> structuralSearch(String query, List<String> repositoryIds);

    /**
     * Execute a semantic (vector) search.
     *
     * @param query Natural language query
     * @param repositoryIds Repositories to search in
     * @param topK Number of results to return
     * @return Similar code entities
     */
    List<SearchResult> semanticSearch(String query, List<String> repositoryIds, int topK);

    /**
     * Search git history.
     *
     * @param query History query (file, author, date range)
     * @param repositoryIds Repositories to search in
     * @return History-based results
     */
    List<SearchResult> temporalSearch(String query, List<String> repositoryIds);

    /**
     * Find dependencies of an entity.
     *
     * @param entityId Entity to find dependencies for
     * @param depth How many levels deep to traverse
     * @param direction Direction to traverse
     * @return Dependency tree
     */
    DependencyResult findDependencies(String entityId, int depth, DependencyDirection direction);

    /**
     * Explain the relationship between two entities.
     *
     * @param fromEntityId Source entity
     * @param toEntityId Target entity
     * @return Explanation of relationship path
     */
    RelationshipExplanation explainRelationship(String fromEntityId, String toEntityId);

    /**
     * Search options for customizing search behavior.
     */
    interface SearchOptions {
        List<String> getRepositoryIds();
        SearchResult.SearchMode getPreferredMode();
        int getMaxResults();
        Map<String, String> getFilters();
        boolean isIncludeSourceCode();
    }

    /**
     * Result of a dependency search.
     */
    interface DependencyResult {
        String getRootEntityId();
        List<DependencyNode> getDependencies();
        int getTotalCount();
    }

    /**
     * Single node in a dependency tree.
     */
    interface DependencyNode {
        String getEntityId();
        String getRelationshipType();
        int getDepth();
        List<DependencyNode> getChildren();
    }

    /**
     * Dependency traversal direction.
     */
    enum DependencyDirection {
        INCOMING,   // Who depends on this entity
        OUTGOING,   // What this entity depends on
        BOTH
    }

    /**
     * Explanation of relationship between entities.
     */
    interface RelationshipExplanation {
        String getFromEntityId();
        String getToEntityId();
        List<String> getPath();
        String getExplanation();
    }
}
