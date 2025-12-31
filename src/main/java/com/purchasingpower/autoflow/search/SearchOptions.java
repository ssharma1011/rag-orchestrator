package com.purchasingpower.autoflow.search;

import com.purchasingpower.autoflow.core.SearchMode;

import java.util.List;
import java.util.Map;

/**
 * Search options for customizing search behavior.
 *
 * @since 2.0.0
 */
public interface SearchOptions {
    List<String> getRepositoryIds();
    SearchMode getPreferredMode();
    int getMaxResults();
    Map<String, String> getFilters();
    boolean isIncludeSourceCode();
}
