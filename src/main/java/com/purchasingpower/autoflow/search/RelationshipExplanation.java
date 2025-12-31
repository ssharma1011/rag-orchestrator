package com.purchasingpower.autoflow.search;

import java.util.List;

/**
 * Explanation of relationship between entities.
 *
 * @since 2.0.0
 */
public interface RelationshipExplanation {
    String getFromEntityId();
    String getToEntityId();
    List<String> getPath();
    String getExplanation();
}
