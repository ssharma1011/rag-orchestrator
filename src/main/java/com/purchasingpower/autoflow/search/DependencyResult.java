package com.purchasingpower.autoflow.search;

import java.util.List;

/**
 * Result of a dependency search.
 *
 * @since 2.0.0
 */
public interface DependencyResult {
    String getRootEntityId();
    List<DependencyNode> getDependencies();
    int getTotalCount();
}
