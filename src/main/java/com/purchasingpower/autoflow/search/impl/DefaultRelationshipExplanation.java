package com.purchasingpower.autoflow.search.impl;

import com.purchasingpower.autoflow.search.RelationshipExplanation;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Default implementation of RelationshipExplanation.
 *
 * @since 2.0.0
 */
@Data
@AllArgsConstructor
public class DefaultRelationshipExplanation implements RelationshipExplanation {
    private final String fromEntityId;
    private final String toEntityId;
    private final List<String> path;
    private final String explanation;
}
