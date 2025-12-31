package com.purchasingpower.autoflow.core;

import java.util.List;
import java.util.Map;

/**
 * Unified search result from any search mode (structural, semantic, temporal).
 *
 * <p>Provides a consistent interface for all search results regardless of
 * the underlying search strategy used.
 *
 * @since 2.0.0
 */
public interface SearchResult {

    /**
     * Unique identifier for the matched entity.
     */
    String getEntityId();

    /**
     * Type of entity matched.
     */
    CodeEntity.EntityType getEntityType();

    /**
     * Repository containing this result.
     */
    String getRepositoryId();

    /**
     * Relevance score (0.0 to 1.0).
     * For structural queries, this is typically 1.0.
     * For semantic queries, this is the similarity score.
     */
    float getScore();

    /**
     * Fully qualified name of the matched entity.
     */
    String getFullyQualifiedName();

    /**
     * File path to the source.
     */
    String getFilePath();

    /**
     * Content or excerpt from the matched entity.
     */
    String getContent();

    /**
     * How this result was found.
     */
    SearchMode getSearchMode();

    /**
     * Additional metadata about the match.
     */
    Map<String, Object> getMetadata();

    /**
     * Related entities discovered during search.
     */
    List<String> getRelatedEntityIds();

    /**
     * Search modes available.
     */
    enum SearchMode {
        /**
         * Found via graph query (relationships, structure).
         */
        STRUCTURAL,

        /**
         * Found via vector similarity search.
         */
        SEMANTIC,

        /**
         * Found via git history analysis.
         */
        TEMPORAL,

        /**
         * Combined search using multiple modes.
         */
        HYBRID
    }
}
