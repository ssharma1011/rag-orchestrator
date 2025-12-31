package com.purchasingpower.autoflow.search;

import java.util.List;

/**
 * Single node in a dependency tree.
 *
 * @since 2.0.0
 */
public interface DependencyNode {
    String getEntityId();
    String getRelationshipType();
    int getDepth();
    List<DependencyNode> getChildren();
}
