package com.purchasingpower.autoflow.search;

import com.purchasingpower.autoflow.core.SearchResult;

import java.util.List;

/**
 * Unified search service orchestrating all search modes.
 *
 * @since 2.0.0
 */
public interface SearchService {

    List<SearchResult> search(String query, SearchOptions options);

    List<SearchResult> structuralSearch(String query, List<String> repositoryIds);

    List<SearchResult> semanticSearch(String query, List<String> repositoryIds, int topK);

    List<SearchResult> temporalSearch(String query, List<String> repositoryIds);

    DependencyResult findDependencies(String entityId, int depth, DependencyDirection direction);

    RelationshipExplanation explainRelationship(String fromEntityId, String toEntityId);
}
