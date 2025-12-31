package com.purchasingpower.autoflow.search.impl;

import com.purchasingpower.autoflow.search.DependencyNode;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Default implementation of DependencyNode.
 *
 * @since 2.0.0
 */
@Data
@AllArgsConstructor
public class DefaultDependencyNode implements DependencyNode {
    private final String entityId;
    private final String relationshipType;
    private final int depth;
    private final List<DependencyNode> children;
}
