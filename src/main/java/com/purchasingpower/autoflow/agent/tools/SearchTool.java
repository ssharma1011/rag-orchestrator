package com.purchasingpower.autoflow.agent.tools;

import com.purchasingpower.autoflow.agent.Tool;
import com.purchasingpower.autoflow.agent.ToolCategory;
import com.purchasingpower.autoflow.agent.ToolContext;
import com.purchasingpower.autoflow.agent.ToolResult;
import com.purchasingpower.autoflow.core.SearchResult;
import com.purchasingpower.autoflow.search.SearchService;
import com.purchasingpower.autoflow.search.impl.DefaultSearchOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tool for searching code in indexed repositories.
 *
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchTool implements Tool {

    private final SearchService searchService;

    @Override
    public String getName() {
        return "search_code";
    }

    @Override
    public String getDescription() {
        return "Search for code using exact keyword matching. Use for finding specific class/method names, file paths, or exact text. For conceptual/semantic queries, use semantic_search instead.";
    }

    @Override
    public String getParameterSchema() {
        return "{\"query\": \"string (required) - what to search for\", \"max_results\": \"integer (optional, default 10)\"}";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.KNOWLEDGE;
    }

    @Override
    public boolean requiresIndexedRepo() {
        return true; // Search requires indexed code
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String query = (String) parameters.get("query");
        if (query == null || query.isBlank()) {
            return ToolResult.failure("Query parameter is required");
        }

        int maxResults = 10;
        if (parameters.containsKey("max_results")) {
            maxResults = ((Number) parameters.get("max_results")).intValue();
        }

        // Check if this search was already attempted
        int executionCount = context.getToolExecutionCount("search_code");
        boolean needsBetterResults = context.hasNegativeFeedback();

        // Refine query if needed
        List<String> queries = refineQuery(query, executionCount, needsBetterResults);
        log.info("Searching with {} queries (execution #{}, feedback={}): {}",
            queries.size(), executionCount + 1, needsBetterResults, queries);

        try {
            DefaultSearchOptions options = DefaultSearchOptions.builder()
                .repositoryIds(context.getRepositoryIds())
                .maxResults(maxResults)
                .build();

            // Search with all refined queries and merge results
            List<SearchResult> allResults = new java.util.ArrayList<>();
            for (String q : queries) {
                List<SearchResult> results = searchService.search(q, options);
                allResults.addAll(results);
            }

            // Remove duplicates and sort by score
            List<SearchResult> uniqueResults = allResults.stream()
                .distinct()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(maxResults)
                .collect(Collectors.toList());

            ToolResult result;
            if (uniqueResults.isEmpty()) {
                result = ToolResult.success(
                    Map.of("count", 0, "results", List.of(), "queriesUsed", queries),
                    "No results found for: " + query
                );
            } else {
                List<Map<String, Object>> formattedResults = uniqueResults.stream()
                    .map(this::formatResult)
                    .collect(Collectors.toList());

                result = ToolResult.success(
                    Map.of("count", uniqueResults.size(), "results", formattedResults, "queriesUsed", queries),
                    "Found " + uniqueResults.size() + " results (searched " + queries.size() + " variations)"
                );
            }

            // Record execution for learning
            context.recordToolExecution("search_code", result, null);
            return result;

        } catch (Exception e) {
            log.error("Search failed", e);
            return ToolResult.failure("Search failed: " + e.getMessage());
        }
    }

    /**
     * Refine query based on execution history and feedback.
     * Returns multiple query variations to try.
     */
    private List<String> refineQuery(String originalQuery, int executionCount, boolean needsBetter) {
        List<String> queries = new java.util.ArrayList<>();
        queries.add(originalQuery);

        if (executionCount == 0 && !needsBetter) {
            // First attempt - use original query only
            return queries;
        }

        // Generate alternative search terms
        String[] words = originalQuery.toLowerCase().split("\\s+");

        if (words.length == 1) {
            String word = words[0];
            // Single word - try variations
            queries.add(word + "Impl");
            queries.add(word + "Service");
            queries.add(word + "Controller");
            queries.add(word + "Repository");
            queries.add("I" + capitalize(word)); // Interface pattern
        } else {
            // Multiple words - try different combinations
            for (String word : words) {
                if (word.length() > 3) { // Skip short words
                    queries.add(word);
                }
            }

            // Try camelCase combination
            String camelCase = toCamelCase(words);
            if (!camelCase.equals(originalQuery)) {
                queries.add(camelCase);
            }
        }

        log.debug("Refined query '{}' into {} variations", originalQuery, queries.size());
        return queries.stream().distinct().collect(Collectors.toList());
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private String toCamelCase(String[] words) {
        if (words.length == 0) return "";
        StringBuilder result = new StringBuilder(words[0].toLowerCase());
        for (int i = 1; i < words.length; i++) {
            result.append(capitalize(words[i]));
        }
        return result.toString();
    }

    private Map<String, Object> formatResult(SearchResult result) {
        return Map.of(
            "entityId", result.getEntityId() != null ? result.getEntityId() : "",
            "type", result.getEntityType() != null ? result.getEntityType().name() : "UNKNOWN",
            "name", result.getFullyQualifiedName() != null ? result.getFullyQualifiedName() : "",
            "file", result.getFilePath() != null ? result.getFilePath() : "",
            "score", result.getScore(),
            "snippet", truncate(result.getContent(), 500)
        );
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
