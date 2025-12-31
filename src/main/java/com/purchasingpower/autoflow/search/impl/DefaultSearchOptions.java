package com.purchasingpower.autoflow.search.impl;

import com.purchasingpower.autoflow.core.SearchMode;
import com.purchasingpower.autoflow.search.SearchOptions;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of SearchOptions.
 *
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DefaultSearchOptions implements SearchOptions {

    private List<String> repositoryIds;
    private SearchMode preferredMode;

    @Builder.Default
    private int maxResults = 20;

    @Builder.Default
    private Map<String, String> filters = new HashMap<>();

    @Builder.Default
    private boolean includeSourceCode = true;
}
