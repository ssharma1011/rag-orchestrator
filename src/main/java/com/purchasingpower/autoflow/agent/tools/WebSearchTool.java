package com.purchasingpower.autoflow.agent.tools;

import com.purchasingpower.autoflow.agent.Tool;
import com.purchasingpower.autoflow.agent.ToolCategory;
import com.purchasingpower.autoflow.agent.ToolContext;
import com.purchasingpower.autoflow.agent.ToolResult;
import com.purchasingpower.autoflow.service.search.WebSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool for searching the web for current library documentation and API information.
 *
 * <p>Used as fallback when:
 * - Compilation fails with "cannot find symbol" or "incompatible types"
 * - APIs are deprecated or method signatures have changed
 * - Need latest library documentation not in indexed code
 *
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchTool implements Tool {

    private final WebSearchService webSearchService;

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        return "Search the web for current library documentation, API signatures, and solutions to compilation errors. " +
               "Use when compilation fails or when you need latest documentation not available in the codebase.";
    }

    @Override
    public String getParameterSchema() {
        return "{\"query\": \"string (required) - search query (e.g., 'Neo4j executeWrite Java API 2024')\", " +
               "\"library\": \"string (optional) - specific library name to focus search (e.g., 'Neo4j', 'Spring Boot')\"}";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.UNDERSTANDING;
    }

    @Override
    public boolean requiresIndexedRepo() {
        return false; // Web search doesn't need indexed code
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        if (!webSearchService.isEnabled()) {
            return ToolResult.failure("Web search is disabled. Enable with WEB_SEARCH_ENABLED=true and provide TAVILY_API_KEY");
        }

        String query = (String) parameters.get("query");
        if (query == null || query.isBlank()) {
            return ToolResult.failure("query parameter is required");
        }

        String library = (String) parameters.get("library");

        log.info("Web search: {} {}", query, library != null ? "(library: " + library + ")" : "");

        try {
            com.purchasingpower.autoflow.service.search.SearchResult result;

            if (library != null && !library.isBlank()) {
                // Search specific library documentation
                result = webSearchService.searchLibraryAPI(library, query);
            } else {
                // General search
                result = webSearchService.search(query);
            }

            if (result.getConfidence() == 0) {
                return ToolResult.failure("No results found for: " + query);
            }

            return ToolResult.success(
                Map.of(
                    "query", result.getQuery(),
                    "answer", result.getAnswer(),
                    "confidence", result.getConfidence(),
                    "sources", result.getSources(),
                    "searchTimeMs", result.getSearchTimeMs()
                ),
                String.format("Found answer with %d%% confidence from %d sources",
                    result.getConfidence(), result.getSources().size())
            );

        } catch (Exception e) {
            log.error("Web search failed", e);
            return ToolResult.failure("Web search failed: " + e.getMessage());
        }
    }
}
