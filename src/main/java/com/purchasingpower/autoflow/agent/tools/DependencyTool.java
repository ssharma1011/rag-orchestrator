package com.purchasingpower.autoflow.agent.tools;

import com.purchasingpower.autoflow.agent.Tool;
import com.purchasingpower.autoflow.agent.ToolCategory;
import com.purchasingpower.autoflow.agent.ToolContext;
import com.purchasingpower.autoflow.agent.ToolResult;
import com.purchasingpower.autoflow.search.DependencyDirection;
import com.purchasingpower.autoflow.search.DependencyResult;
import com.purchasingpower.autoflow.search.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tool for finding dependencies and relationships between code entities.
 *
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DependencyTool implements Tool {

    private final SearchService searchService;

    @Override
    public String getName() {
        return "find_dependencies";
    }

    @Override
    public String getDescription() {
        return "Find what a class/method depends on or what depends on it. Useful for understanding code relationships and impact analysis.";
    }

    @Override
    public String getParameterSchema() {
        return "{\"entity_id\": \"string (required) - ID of class or method\", \"direction\": \"string (optional: 'upstream', 'downstream', 'both', default 'both')\", \"depth\": \"integer (optional, default 2)\"}";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.KNOWLEDGE;
    }

    @Override
    public boolean requiresIndexedRepo() {
        return true; // Dependency analysis requires indexed code
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String entityId = (String) parameters.get("entity_id");
        if (entityId == null || entityId.isBlank()) {
            return ToolResult.failure("entity_id parameter is required");
        }

        String directionStr = (String) parameters.getOrDefault("direction", "both");
        int depth = 2;
        if (parameters.containsKey("depth")) {
            depth = ((Number) parameters.get("depth")).intValue();
        }

        DependencyDirection direction = switch (directionStr.toLowerCase()) {
            case "upstream", "incoming" -> DependencyDirection.INCOMING;
            case "downstream", "outgoing" -> DependencyDirection.OUTGOING;
            default -> DependencyDirection.BOTH;
        };

        log.info("Finding dependencies for {} ({}, depth {})", entityId, direction, depth);

        try {
            DependencyResult result = searchService.findDependencies(entityId, depth, direction);

            List<Map<String, Object>> deps = result.getDependencies().stream()
                .map(node -> Map.<String, Object>of(
                    "id", node.getEntityId(),
                    "relationship", node.getRelationshipType(),
                    "depth", node.getDepth()
                ))
                .collect(Collectors.toList());

            return ToolResult.success(
                Map.of(
                    "rootEntity", result.getRootEntityId(),
                    "count", result.getTotalCount(),
                    "dependencies", deps
                ),
                "Found " + result.getTotalCount() + " dependencies"
            );

        } catch (Exception e) {
            log.error("Dependency lookup failed", e);
            return ToolResult.failure("Failed to find dependencies: " + e.getMessage());
        }
    }
}
