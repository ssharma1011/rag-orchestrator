package com.purchasingpower.autoflow.search.impl;

import com.purchasingpower.autoflow.search.DependencyNode;
import com.purchasingpower.autoflow.search.DependencyResult;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Default implementation of DependencyResult.
 *
 * @since 2.0.0
 */
@Data
@AllArgsConstructor
public class DefaultDependencyResult implements DependencyResult {
    private final String rootEntityId;
    private final List<DependencyNode> dependencies;
    private final int totalCount;
}
