package com.purchasingpower.autoflow.api;

import com.purchasingpower.autoflow.core.SearchResult;
import com.purchasingpower.autoflow.knowledge.GraphStore;
import com.purchasingpower.autoflow.search.SearchService;
import com.purchasingpower.autoflow.search.impl.DefaultSearchOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for search API.
 *
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final GraphStore graphStore;

    /**
     * Hybrid search.
     *
     * POST /api/v1/search
     */
    @PostMapping
    public ResponseEntity<SearchResponse> search(@RequestBody SearchRequest request) {
        try {
            if (request.getQuery() == null || request.getQuery().isBlank()) {
                return ResponseEntity.badRequest()
                    .body(SearchResponse.error("Query is required"));
            }

            log.info("Search: {}", request.getQuery());

            DefaultSearchOptions options = DefaultSearchOptions.builder()
                .repositoryIds(request.getRepoIds())
                .maxResults(request.getMaxResults() != null ? request.getMaxResults() : 20)
                .build();

            List<SearchResult> results = searchService.search(request.getQuery(), options);

            List<SearchResponse.Result> formattedResults = results.stream()
                .map(r -> SearchResponse.Result.builder()
                    .entityId(r.getEntityId())
                    .type(r.getEntityType() != null ? r.getEntityType().name() : "UNKNOWN")
                    .name(r.getFullyQualifiedName())
                    .filePath(r.getFilePath())
                    .snippet(truncate(r.getContent(), 500))
                    .score(r.getScore())
                    .build())
                .collect(Collectors.toList());

            return ResponseEntity.ok(SearchResponse.success(formattedResults));

        } catch (Exception e) {
            log.error("Search failed", e);
            return ResponseEntity.internalServerError()
                .body(SearchResponse.error("Search failed: " + e.getMessage()));
        }
    }

    /**
     * List indexed repositories.
     *
     * GET /api/v1/search/repos
     */
    @GetMapping("/repos")
    public ResponseEntity<List<Map<String, Object>>> listRepos() {
        try {
            String cypher = "MATCH (r:Repository) RETURN r.id as id, r.name as name, r.url as url, r.language as language";
            List<Map<String, Object>> repos = graphStore.executeCypherQueryRaw(cypher, Map.of());
            return ResponseEntity.ok(repos);
        } catch (Exception e) {
            log.error("Failed to list repos", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Execute raw graph query.
     *
     * POST /api/v1/search/graph
     */
    @PostMapping("/graph")
    public ResponseEntity<GraphQueryResponse> graphQuery(@RequestBody GraphQueryRequest request) {
        try {
            if (request.getCypher() == null || request.getCypher().isBlank()) {
                return ResponseEntity.badRequest()
                    .body(GraphQueryResponse.error("Cypher query is required"));
            }

            if (!isSafeQuery(request.getCypher())) {
                return ResponseEntity.badRequest()
                    .body(GraphQueryResponse.error("Only read queries allowed"));
            }

            log.info("Graph query: {}", truncate(request.getCypher(), 100));

            Map<String, Object> params = request.getParameters() != null ? request.getParameters() : Map.of();
            List<Map<String, Object>> results = graphStore.executeCypherQueryRaw(request.getCypher(), params);

            return ResponseEntity.ok(GraphQueryResponse.success(results));

        } catch (Exception e) {
            log.error("Graph query failed", e);
            return ResponseEntity.internalServerError()
                .body(GraphQueryResponse.error("Query failed: " + e.getMessage()));
        }
    }

    private boolean isSafeQuery(String cypher) {
        String upper = cypher.toUpperCase();
        return !upper.contains("DELETE") &&
               !upper.contains("REMOVE") &&
               !upper.contains("SET") &&
               !upper.contains("CREATE") &&
               !upper.contains("MERGE") &&
               !upper.contains("DROP");
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
