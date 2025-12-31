package com.purchasingpower.autoflow.core;

import java.util.List;
import java.util.Map;

/**
 * Unified search result from any search mode.
 *
 * @since 2.0.0
 */
public interface SearchResult {

    String getEntityId();

    EntityType getEntityType();

    String getRepositoryId();

    float getScore();

    String getFullyQualifiedName();

    String getFilePath();

    String getContent();

    SearchMode getSearchMode();

    Map<String, Object> getMetadata();

    List<String> getRelatedEntityIds();
}
