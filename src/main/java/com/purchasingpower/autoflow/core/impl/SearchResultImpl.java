package com.purchasingpower.autoflow.core.impl;

import com.purchasingpower.autoflow.core.EntityType;
import com.purchasingpower.autoflow.core.SearchMode;
import com.purchasingpower.autoflow.core.SearchResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of SearchResult.
 *
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultImpl implements SearchResult {

    private String entityId;
    private EntityType entityType;
    private String repositoryId;
    private float score;
    private String fullyQualifiedName;
    private String filePath;
    private String content;
    private SearchMode searchMode;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Builder.Default
    private List<String> relatedEntityIds = new ArrayList<>();
}
