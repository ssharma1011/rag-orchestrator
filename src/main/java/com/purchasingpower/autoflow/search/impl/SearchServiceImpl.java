package com.purchasingpower.autoflow.search.impl;

import com.purchasingpower.autoflow.core.EntityType;
import com.purchasingpower.autoflow.core.SearchMode;
import com.purchasingpower.autoflow.core.SearchResult;
import com.purchasingpower.autoflow.core.impl.SearchResultImpl;
import com.purchasingpower.autoflow.knowledge.GraphStore;
import com.purchasingpower.autoflow.search.DependencyDirection;
import com.purchasingpower.autoflow.search.DependencyNode;
import com.purchasingpower.autoflow.search.DependencyResult;
import com.purchasingpower.autoflow.search.RelationshipExplanation;
import com.purchasingpower.autoflow.search.SearchOptions;
import com.purchasingpower.autoflow.search.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of SearchService.
 *
 * Orchestrates structural, semantic, and temporal search modes.
 *
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final GraphStore graphStore;

    @Override
    public List<SearchResult> search(String query, SearchOptions options) {
        log.info("Searching with query: {}", query);

        SearchMode mode = options != null && options.getPreferredMode() != null
            ? options.getPreferredMode()
            : detectBestSearchMode(query);

        return switch (mode) {
            case STRUCTURAL -> structuralSearch(query, options != null ? options.getRepositoryIds() : null);
            case SEMANTIC -> semanticSearch(query, options != null ? options.getRepositoryIds() : null,
                options != null ? options.getMaxResults() : 20);
            case TEMPORAL -> temporalSearch(query, options != null ? options.getRepositoryIds() : null);
            case HYBRID -> hybridSearch(query, options);
        };
    }

    @Override
    public List<SearchResult> structuralSearch(String query, List<String> repositoryIds) {
        log.debug("Executing structural search: {}", query);

        String cypher = buildStructuralQuery(query, repositoryIds);
        Map<String, Object> params = new HashMap<>();
        if (repositoryIds != null && !repositoryIds.isEmpty()) {
            params.put("repoIds", repositoryIds);
        }

        var entities = graphStore.executeCypherQuery(cypher, params);

        List<SearchResult> results = new ArrayList<>();
        for (var entity : entities) {
            results.add(SearchResultImpl.builder()
                .entityId(entity.getId())
                .entityType(entity.getType())
                .repositoryId(entity.getRepositoryId())
                .fullyQualifiedName(entity.getFullyQualifiedName())
                .filePath(entity.getFilePath())
                .content(entity.getSourceCode())
                .score(1.0f)
                .searchMode(SearchMode.STRUCTURAL)
                .build());
        }

        return results;
    }

    @Override
    public List<SearchResult> semanticSearch(String query, List<String> repositoryIds, int topK) {
        log.debug("Executing semantic search: {}", query);

        // For now, fall back to structural search with CONTAINS
        // TODO: Implement vector search when embeddings are added to Neo4j
        String cypher = """
            MATCH (e:Entity)
            WHERE (e.name CONTAINS $query OR e.fullyQualifiedName CONTAINS $query OR e.sourceCode CONTAINS $query)
            """ + (repositoryIds != null && !repositoryIds.isEmpty() ? " AND e.repositoryId IN $repoIds" : "") + """
            RETURN e
            LIMIT $limit
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("limit", topK);
        if (repositoryIds != null && !repositoryIds.isEmpty()) {
            params.put("repoIds", repositoryIds);
        }

        var entities = graphStore.executeCypherQuery(cypher, params);

        List<SearchResult> results = new ArrayList<>();
        for (var entity : entities) {
            results.add(SearchResultImpl.builder()
                .entityId(entity.getId())
                .entityType(entity.getType())
                .repositoryId(entity.getRepositoryId())
                .fullyQualifiedName(entity.getFullyQualifiedName())
                .filePath(entity.getFilePath())
                .content(entity.getSourceCode())
                .score(0.8f)
                .searchMode(SearchMode.SEMANTIC)
                .build());
        }

        return results;
    }

    @Override
    public List<SearchResult> temporalSearch(String query, List<String> repositoryIds) {
        log.debug("Executing temporal search: {}", query);

        // TODO: Implement git history analysis
        // For now, return empty - temporal search requires git integration
        log.warn("Temporal search not yet implemented, returning empty results");
        return new ArrayList<>();
    }

    @Override
    public DependencyResult findDependencies(String entityId, int depth, DependencyDirection direction) {
        log.debug("Finding dependencies for: {}, depth: {}", entityId, depth);

        var relDirection = switch (direction) {
            case INCOMING -> com.purchasingpower.autoflow.knowledge.RelationshipDirection.INCOMING;
            case OUTGOING -> com.purchasingpower.autoflow.knowledge.RelationshipDirection.OUTGOING;
            case BOTH -> com.purchasingpower.autoflow.knowledge.RelationshipDirection.BOTH;
        };

        var related = graphStore.findRelatedEntities(entityId, null, relDirection);

        List<DependencyNode> nodes = new ArrayList<>();
        for (var entity : related) {
            nodes.add(new DefaultDependencyNode(entity.getId(), "DEPENDS_ON", 1, new ArrayList<>()));
        }

        return new DefaultDependencyResult(entityId, nodes, nodes.size());
    }

    @Override
    public RelationshipExplanation explainRelationship(String fromEntityId, String toEntityId) {
        log.debug("Explaining relationship between {} and {}", fromEntityId, toEntityId);

        String cypher = """
            MATCH path = shortestPath((from:Entity {id: $fromId})-[*..5]-(to:Entity {id: $toId}))
            RETURN path
            """;

        var results = graphStore.executeCypherQueryRaw(cypher, Map.of("fromId", fromEntityId, "toId", toEntityId));

        List<String> pathNodes = new ArrayList<>();
        pathNodes.add(fromEntityId);
        pathNodes.add(toEntityId);

        String explanation = results.isEmpty()
            ? "No direct relationship found between these entities"
            : "Connected via " + results.size() + " path(s)";

        return new DefaultRelationshipExplanation(fromEntityId, toEntityId, pathNodes, explanation);
    }

    private SearchMode detectBestSearchMode(String query) {
        String lower = query.toLowerCase();

        // Structural indicators
        if (lower.contains("call") || lower.contains("depend") || lower.contains("extend")
            || lower.contains("implement") || lower.contains("relationship")) {
            return SearchMode.STRUCTURAL;
        }

        // Temporal indicators
        if (lower.contains("change") || lower.contains("history") || lower.contains("recent")
            || lower.contains("modified") || lower.contains("commit")) {
            return SearchMode.TEMPORAL;
        }

        // Default to semantic for natural language queries
        return SearchMode.SEMANTIC;
    }

    private String buildStructuralQuery(String query, List<String> repositoryIds) {
        // Simple keyword-based query builder
        String base = "MATCH (e:Entity) WHERE ";

        List<String> conditions = new ArrayList<>();

        // Extract potential class/method names from query
        String[] words = query.split("\\s+");
        for (String word : words) {
            if (word.length() > 2 && Character.isUpperCase(word.charAt(0))) {
                conditions.add("e.name CONTAINS '" + word + "'");
            }
        }

        if (conditions.isEmpty()) {
            conditions.add("e.name CONTAINS '" + query + "'");
        }

        if (repositoryIds != null && !repositoryIds.isEmpty()) {
            conditions.add("e.repositoryId IN $repoIds");
        }

        return base + String.join(" OR ", conditions) + " RETURN e LIMIT 50";
    }

    private List<SearchResult> hybridSearch(String query, SearchOptions options) {
        List<SearchResult> structural = structuralSearch(query, options != null ? options.getRepositoryIds() : null);
        List<SearchResult> semantic = semanticSearch(query, options != null ? options.getRepositoryIds() : null, 10);

        // Merge and deduplicate
        Map<String, SearchResult> merged = new HashMap<>();
        for (var result : structural) {
            merged.put(result.getEntityId(), result);
        }
        for (var result : semantic) {
            if (!merged.containsKey(result.getEntityId())) {
                merged.put(result.getEntityId(), result);
            }
        }

        return new ArrayList<>(merged.values());
    }
}
