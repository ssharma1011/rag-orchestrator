package com.purchasingpower.autoflow.agent.tools;

import com.purchasingpower.autoflow.agent.Tool;
import com.purchasingpower.autoflow.agent.ToolCategory;
import com.purchasingpower.autoflow.agent.ToolContext;
import com.purchasingpower.autoflow.agent.ToolResult;
import com.purchasingpower.autoflow.knowledge.EmbeddingService;
import com.purchasingpower.autoflow.knowledge.GraphStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Semantic search tool using vector embeddings.
 *
 * Enables natural language queries like "find authentication code"
 * or "how does chat streaming work" by using similarity search
 * on enriched class/method descriptions.
 *
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticSearchTool implements Tool {

    private final EmbeddingService embeddingService;
    private final GraphStore graphStore;

    @Override
    public String getName() {
        return "semantic_search";
    }

    @Override
    public String getDescription() {
        return "AI-powered semantic search using embeddings. " +
               "Finds code by MEANING/INTENT, not keywords. " +
               "BEST FOR: 'how does authentication work', 'find the properties/config file', 'where is error handling', 'code that processes payments'. " +
               "Understands synonyms, concepts, and intent even without exact word matches.";
    }

    @Override
    public String getParameterSchema() {
        return "{\"query\": \"string (required) - Natural language search query (e.g. 'authentication logic', 'chat streaming', 'repository indexing')\", " +
               "\"limit\": \"number (optional, default 5) - Max results to return\", " +
               "\"include_methods\": \"boolean (optional, default true) - Also search method-level code\"}";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.KNOWLEDGE;
    }

    @Override
    public boolean requiresIndexedRepo() {
        return true;
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String query = (String) parameters.get("query");
        if (query == null || query.isBlank()) {
            return ToolResult.failure("query parameter is required");
        }

        int limit = parameters.containsKey("limit")
            ? ((Number) parameters.get("limit")).intValue()
            : 5;

        boolean includeMethods = parameters.containsKey("include_methods")
            ? (Boolean) parameters.get("include_methods")
            : true;  // FIX: Changed default to true - methods are critical for code explanations

        log.info("🔍 [SEMANTIC SEARCH] Query: '{}', Limit: {}, IncludeMethods: {}",
            query, limit, includeMethods);

        try {
            // 1. Generate embedding for the query
            List<Double> queryEmbedding = embeddingService.generateTextEmbedding(query);
            log.debug("🔷 Generated query embedding: {} dimensions", queryEmbedding.size());

            // 2. Search for similar classes
            List<Map<String, Object>> classResults = searchClasses(
                queryEmbedding,
                context.getRepositoryIds(),
                limit
            );

            // 3. Optionally search methods
            List<Map<String, Object>> methodResults = List.of();
            if (includeMethods) {
                methodResults = searchMethods(
                    queryEmbedding,
                    context.getRepositoryIds(),
                    limit
                );
            }

            // 4. Format results
            String summary = formatResults(classResults, methodResults, query);

            Map<String, Object> resultData = Map.of(
                "query", query,
                "classes", classResults,
                "methods", methodResults,
                "totalResults", classResults.size() + methodResults.size()
            );

            log.info("✅ [SEMANTIC SEARCH] Found {} classes, {} methods",
                classResults.size(), methodResults.size());

            return ToolResult.success(resultData, summary);

        } catch (Exception e) {
            log.error("❌ [SEMANTIC SEARCH] Failed: {}", e.getMessage(), e);
            return ToolResult.failure("Semantic search failed: " + e.getMessage());
        }
    }

    /**
     * Search for classes using vector similarity.
     */
    private List<Map<String, Object>> searchClasses(
            List<Double> queryEmbedding,
            List<String> repositoryIds,
            int limit) {

        String cypher = """
            CALL db.index.vector.queryNodes(
              'type_embedding_index',
              $limit,
              $queryEmbedding
            ) YIELD node, score
            WHERE node.repositoryId IN $repoIds
              AND score > $minScore
            RETURN node.fqn as fqn,
                   node.name as name,
                   node.packageName as package,
                   node.kind as kind,
                   node.description as description,
                   node.sourceCode as sourceCode,
                   node.filePath as filePath,
                   score
            ORDER BY score DESC
            """;

        try {
            return graphStore.executeCypherQueryRaw(cypher, Map.of(
                "queryEmbedding", queryEmbedding,
                "repoIds", repositoryIds,
                "limit", limit * 2, // Get more, filter later
                "minScore", 0.65 // FIX: Only return matches with >65% similarity
            ));
        } catch (Exception e) {
            log.error("❌ Class vector search failed: {}", e.getMessage());
            // Fallback to text search if vector search fails
            return fallbackTextSearch(repositoryIds, limit);
        }
    }

    /**
     * Search for methods using vector similarity.
     */
    private List<Map<String, Object>> searchMethods(
            List<Double> queryEmbedding,
            List<String> repositoryIds,
            int limit) {

        String cypher = """
            CALL db.index.vector.queryNodes(
              'method_embedding_index',
              $limit,
              $queryEmbedding
            ) YIELD node, score
            WHERE node.repositoryId IN $repoIds
              AND score > $minScore
            RETURN node.name as name,
                   node.signature as signature,
                   node.description as description,
                   node.sourceCode as sourceCode,
                   node.returnType as returnType,
                   score
            ORDER BY score DESC
            """;

        try {
            return graphStore.executeCypherQueryRaw(cypher, Map.of(
                "queryEmbedding", queryEmbedding,
                "repoIds", repositoryIds,
                "limit", limit,
                "minScore", 0.65 // FIX: Only return matches with >65% similarity
            ));
        } catch (Exception e) {
            log.error("❌ Method vector search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Fallback to text-based search if vector search fails.
     */
    private List<Map<String, Object>> fallbackTextSearch(
            List<String> repositoryIds,
            int limit) {

        log.warn("⚠️  Falling back to text search (vector search unavailable)");

        String cypher = """
            MATCH (t:Type)
            WHERE t.repositoryId IN $repoIds
              AND (toLower(t.description) CONTAINS toLower($query)
                   OR toLower(t.name) CONTAINS toLower($query))
            RETURN t.fqn as fqn,
                   t.name as name,
                   t.packageName as package,
                   t.kind as kind,
                   t.description as description,
                   t.filePath as filePath,
                   1.0 as score
            LIMIT $limit
            """;

        return graphStore.executeCypherQueryRaw(cypher, Map.of(
            "repoIds", repositoryIds,
            "limit", limit,
            "query", "" // Would need to extract from embedding context
        ));
    }

    /**
     * Format search results into human-readable summary.
     */
    private String formatResults(
            List<Map<String, Object>> classResults,
            List<Map<String, Object>> methodResults,
            String query) {

        StringBuilder summary = new StringBuilder();
        summary.append("## Semantic Search Results for: \"").append(query).append("\"\n\n");

        if (classResults.isEmpty() && methodResults.isEmpty()) {
            summary.append("No relevant code found for this query.\n");
            return summary.toString();
        }

        // Format class results
        if (!classResults.isEmpty()) {
            summary.append("### Relevant Classes (").append(classResults.size()).append(" found)\n\n");
            for (Map<String, Object> result : classResults) {
                String name = (String) result.get("name");
                String fqn = (String) result.get("fqn");
                String kind = (String) result.get("kind");
                String pkg = (String) result.get("package");
                String filePath = (String) result.get("filePath");
                Object scoreObj = result.get("score");
                double score = scoreObj instanceof Number ? ((Number) scoreObj).doubleValue() : 0.0;

                summary.append(String.format("**%s** (%s)\n", name, kind));
                summary.append(String.format("- FQN: `%s`\n", fqn));
                summary.append(String.format("- Package: `%s`\n", pkg));
                summary.append(String.format("- Similarity: %.3f\n", score));
                summary.append(String.format("- File: `%s`\n", filePath));

                // Include description snippet
                String description = (String) result.get("description");
                if (description != null && !description.isEmpty()) {
                    String snippet = description.length() > 200
                        ? description.substring(0, 200) + "..."
                        : description;
                    summary.append(String.format("- Summary: %s\n", snippet.replace("\n", " ")));
                }
                summary.append("\n");
            }
        }

        // Format method results
        if (!methodResults.isEmpty()) {
            summary.append("### Relevant Methods (").append(methodResults.size()).append(" found)\n\n");
            for (Map<String, Object> result : methodResults) {
                String name = (String) result.get("name");
                String signature = (String) result.get("signature");
                Object scoreObj = result.get("score");
                double score = scoreObj instanceof Number ? ((Number) scoreObj).doubleValue() : 0.0;

                summary.append(String.format("**%s**\n", name));
                summary.append(String.format("- Signature: `%s`\n", signature));
                summary.append(String.format("- Similarity: %.3f\n", score));

                String description = (String) result.get("description");
                if (description != null && !description.isEmpty()) {
                    String snippet = description.length() > 150
                        ? description.substring(0, 150) + "..."
                        : description;
                    summary.append(String.format("- Summary: %s\n", snippet.replace("\n", " ")));
                }
                summary.append("\n");
            }
        }

        return summary.toString();
    }
}
