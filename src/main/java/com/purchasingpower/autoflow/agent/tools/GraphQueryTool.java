package com.purchasingpower.autoflow.agent.tools;

import com.purchasingpower.autoflow.agent.Tool;
import com.purchasingpower.autoflow.agent.ToolCategory;
import com.purchasingpower.autoflow.agent.ToolContext;
import com.purchasingpower.autoflow.agent.ToolResult;
import com.purchasingpower.autoflow.knowledge.GraphStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Tool for executing graph queries against the knowledge graph.
 *
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphQueryTool implements Tool {

    private final GraphStore graphStore;

    @Override
    public String getName() {
        return "query_graph";
    }

    @Override
    public String getDescription() {
        return "Execute a Cypher query against the code knowledge graph. For advanced structural queries.";
    }

    @Override
    public String getParameterSchema() {
        return "{\"cypher\": \"string (required) - Cypher query\", \"parameters\": \"object (optional) - query parameters\"}";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.KNOWLEDGE;
    }

    @Override
    public boolean requiresIndexedRepo() {
        return true; // Graph queries require indexed code
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String cypher = (String) parameters.get("cypher");
        if (cypher == null || cypher.isBlank()) {
            return ToolResult.failure("cypher parameter is required");
        }

        if (!isQuerySafe(cypher)) {
            return ToolResult.failure("Query contains unsafe operations. Only read queries allowed.");
        }

        Map<String, Object> queryParams = (Map<String, Object>) parameters.getOrDefault("parameters", Map.of());

        log.info("Executing Cypher: {}", truncate(cypher, 100));

        try {
            List<Map<String, Object>> results = graphStore.executeCypherQueryRaw(cypher, queryParams);

            return ToolResult.success(
                Map.of(
                    "count", results.size(),
                    "results", results.size() > 50 ? results.subList(0, 50) : results
                ),
                "Query returned " + results.size() + " results"
            );

        } catch (Exception e) {
            log.error("Graph query failed", e);
            return ToolResult.failure("Query failed: " + e.getMessage());
        }
    }

    private boolean isQuerySafe(String cypher) {
        String upper = cypher.toUpperCase();
        return !upper.contains("DELETE") &&
               !upper.contains("REMOVE") &&
               !upper.contains("SET") &&
               !upper.contains("CREATE") &&
               !upper.contains("MERGE") &&
               !upper.contains("DROP");
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
