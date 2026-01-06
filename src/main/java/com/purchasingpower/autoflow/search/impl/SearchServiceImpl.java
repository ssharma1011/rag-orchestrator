package com.purchasingpower.autoflow.search.impl;

import com.purchasingpower.autoflow.core.EntityType;
import com.purchasingpower.autoflow.core.SearchMode;
import com.purchasingpower.autoflow.core.SearchResult;
import com.purchasingpower.autoflow.core.impl.CodeEntityImpl;
import com.purchasingpower.autoflow.core.impl.SearchResultImpl;
import com.purchasingpower.autoflow.knowledge.GraphStore;
import com.purchasingpower.autoflow.knowledge.RelationshipDirection;
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
 * Full Implementation of SearchService.
 * Orchestrates structural, semantic, and temporal search modes.
 *
 * FIXED: Preserves dots/slashes in tokenizer (README.md fix).
 * FIXED: Implements context capping (MAX 5 results) to prevent LLM quota exhaustion.
 * FIXED: Literal path fallback using valid Cypher.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final GraphStore graphStore;

    // Cap results to prevent overwhelming LLM context (but allow reasonable discovery)
    private static final int CONTEXT_RESULT_CAP = 20;

    @Override
    public List<SearchResult> search(String query, SearchOptions options) {
        log.info("🔍 [SEARCH REQUEST] Query: '{}', Mode: {}", query,
            options != null && options.getPreferredMode() != null ? options.getPreferredMode() : "auto-detect");

        SearchMode mode = options != null && options.getPreferredMode() != null
                ? options.getPreferredMode()
                : detectBestSearchMode(query);

        log.info("🔍 [SEARCH REQUEST] Detected/Using mode: {}", mode);

        long startTime = System.currentTimeMillis();
        List<SearchResult> results = switch (mode) {
            case STRUCTURAL -> structuralSearch(query, options != null ? options.getRepositoryIds() : null);
            case SEMANTIC -> semanticSearch(query, options != null ? options.getRepositoryIds() : null,
                    options != null ? options.getMaxResults() : 20);
            case TEMPORAL -> temporalSearch(query, options != null ? options.getRepositoryIds() : null);
            case HYBRID -> hybridSearch(query, options);
        };

        long duration = System.currentTimeMillis() - startTime;
        log.info("🔍 [SEARCH RESPONSE] Found {} results in {}ms", results.size(), duration);
        log.debug("🔍 [SEARCH RESPONSE] Top results: {}", results.stream()
            .limit(5)
            .map(SearchResult::getFullyQualifiedName)
            .toList());

        return results;
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

            if (results.size() >= CONTEXT_RESULT_CAP) break;
        }

        return results;
    }

    @Override
    public List<SearchResult> semanticSearch(String query, List<String> repositoryIds, int topK) {
        log.info("🔍 [SEMANTIC SEARCH] Starting semantic search for: '{}'", query);

        List<String> keywords = tokenizeQuery(query);
        log.info("🔍 [SEMANTIC SEARCH] Tokenized into {} keywords: {}", keywords.size(), keywords);
        if (keywords.isEmpty() && !isPathLike(query)) {
            return List.of();
        }

        List<SearchResult> results = new ArrayList<>();

        if (!keywords.isEmpty()) {
            // NEW SCHEMA: Search Type, Method, and Field nodes separately
            // Prioritize Type nodes (classes) over Method/Field nodes for better results

            results.addAll(searchTypes(keywords, repositoryIds, topK));
            results.addAll(searchMethods(keywords, repositoryIds, Math.min(10, topK / 2)));
            results.addAll(searchFields(keywords, repositoryIds, Math.min(5, topK / 4)));
        }

        // FALLBACK: Literal Path Matching
        if (results.isEmpty() && isPathLike(query)) {
            String pathQuery = "MATCH (t:Type) WHERE toLower(t.filePath) CONTAINS toLower($path) RETURN t LIMIT 3";
            var pathEntities = graphStore.executeCypherQuery(pathQuery, Map.of("path", query.trim()));
            for (var entity : pathEntities) {
                results.add(SearchResultImpl.builder()
                        .entityId(entity.getId())
                        .entityType(entity.getType())
                        .repositoryId(entity.getRepositoryId())
                        .fullyQualifiedName(entity.getFullyQualifiedName())
                        .filePath(entity.getFilePath())
                        .content(entity.getSourceCode())
                        .score(1.0f)
                        .searchMode(SearchMode.SEMANTIC)
                        .build());
            }
        }

        results.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));

        // Apply result cap
        if (results.size() > CONTEXT_RESULT_CAP) {
            results = results.subList(0, CONTEXT_RESULT_CAP);
        }

        return results;
    }

    /**
     * Searches Type nodes (classes, interfaces, enums) using new schema.
     */
    private List<SearchResult> searchTypes(List<String> keywords, List<String> repositoryIds, int limit) {
        log.debug("🔍 [SEARCH TYPES] Searching for types with keywords: {}", keywords);

        StringBuilder whereClause = new StringBuilder("WHERE (");
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) whereClause.append(" OR ");
            // FIX: Add NULL checks to handle missing properties properly
            whereClause.append(String.format(
                    "(e.name IS NOT NULL AND toLower(e.name) CONTAINS toLower($kw%d)) OR " +
                    "(e.fqn IS NOT NULL AND toLower(e.fqn) CONTAINS toLower($kw%d)) OR " +
                    "(e.sourceCode IS NOT NULL AND toLower(e.sourceCode) CONTAINS toLower($kw%d)) OR " +
                    "(e.filePath IS NOT NULL AND toLower(e.filePath) CONTAINS toLower($kw%d))",
                    i, i, i, i
            ));
        }
        whereClause.append(")");

        if (repositoryIds != null && !repositoryIds.isEmpty()) {
            whereClause.append(" AND e.repositoryId IN $repoIds");
        }

        String cypher = String.format("MATCH (e:Type) %s RETURN e LIMIT $limit", whereClause);

        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < keywords.size(); i++) {
            params.put("kw" + i, keywords.get(i));
        }
        params.put("limit", limit);
        if (repositoryIds != null && !repositoryIds.isEmpty()) {
            params.put("repoIds", repositoryIds);
        }

        var entities = graphStore.executeCypherQuery(cypher, params);
        List<SearchResult> results = new ArrayList<>();

        for (var entity : entities) {
            float score = calculateKeywordScore(entity, keywords);
            results.add(SearchResultImpl.builder()
                    .entityId(entity.getId())
                    .entityType(EntityType.CLASS) // Will be CLASS, INTERFACE, or ENUM from entity.kind
                    .repositoryId(entity.getRepositoryId())
                    .fullyQualifiedName(entity.getFullyQualifiedName() != null ? entity.getFullyQualifiedName() : entity.getName())
                    .filePath(entity.getFilePath())
                    .content(entity.getSourceCode())
                    .score(score * 1.5f) // Boost Type results
                    .searchMode(SearchMode.SEMANTIC)
                    .build());
        }

        log.debug("🔍 [SEARCH TYPES] Found {} type results", results.size());
        return results;
    }

    /**
     * Searches Method nodes using new schema.
     */
    private List<SearchResult> searchMethods(List<String> keywords, List<String> repositoryIds, int limit) {
        log.debug("🔍 [SEARCH METHODS] Searching for methods with keywords: {}", keywords);

        StringBuilder whereClause = new StringBuilder("WHERE (");
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) whereClause.append(" OR ");
            // FIX: Add NULL checks for method properties
            whereClause.append(String.format(
                    "(e.name IS NOT NULL AND toLower(e.name) CONTAINS toLower($kw%d)) OR " +
                    "(e.signature IS NOT NULL AND toLower(e.signature) CONTAINS toLower($kw%d)) OR " +
                    "(e.sourceCode IS NOT NULL AND toLower(e.sourceCode) CONTAINS toLower($kw%d))",
                    i, i, i
            ));
        }
        whereClause.append(")");

        if (repositoryIds != null && !repositoryIds.isEmpty()) {
            whereClause.append(" AND e.repositoryId IN $repoIds");
        }

        String cypher = String.format("MATCH (e:Method) %s RETURN e LIMIT $limit", whereClause);

        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < keywords.size(); i++) {
            params.put("kw" + i, keywords.get(i));
        }
        params.put("limit", limit);
        if (repositoryIds != null && !repositoryIds.isEmpty()) {
            params.put("repoIds", repositoryIds);
        }

        var entities = graphStore.executeCypherQuery(cypher, params);
        List<SearchResult> results = new ArrayList<>();

        for (var entity : entities) {
            float score = calculateKeywordScore(entity, keywords);
            results.add(SearchResultImpl.builder()
                    .entityId(entity.getId())
                    .entityType(EntityType.METHOD)
                    .repositoryId(entity.getRepositoryId())
                    .fullyQualifiedName(entity.getName() + "()")
                    .filePath(entity.getFilePath())
                    .content(entity.getSourceCode())
                    .score(score)
                    .searchMode(SearchMode.SEMANTIC)
                    .build());
        }

        log.debug("🔍 [SEARCH METHODS] Found {} method results", results.size());
        return results;
    }

    /**
     * Searches Field nodes using new schema.
     */
    private List<SearchResult> searchFields(List<String> keywords, List<String> repositoryIds, int limit) {
        log.debug("🔍 [SEARCH FIELDS] Searching for fields with keywords: {}", keywords);

        StringBuilder whereClause = new StringBuilder("WHERE (");
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) whereClause.append(" OR ");
            whereClause.append(String.format(
                    "toLower(e.name) CONTAINS toLower($kw%d) OR " +
                    "toLower(e.type) CONTAINS toLower($kw%d)",
                    i, i
            ));
        }
        whereClause.append(")");

        if (repositoryIds != null && !repositoryIds.isEmpty()) {
            whereClause.append(" AND e.repositoryId IN $repoIds");
        }

        String cypher = String.format("MATCH (e:Field) %s RETURN e LIMIT $limit", whereClause);

        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < keywords.size(); i++) {
            params.put("kw" + i, keywords.get(i));
        }
        params.put("limit", limit);
        if (repositoryIds != null && !repositoryIds.isEmpty()) {
            params.put("repoIds", repositoryIds);
        }

        var entities = graphStore.executeCypherQuery(cypher, params);
        List<SearchResult> results = new ArrayList<>();

        for (var entity : entities) {
            float score = calculateKeywordScore(entity, keywords) * 0.5f; // De-prioritize fields
            results.add(SearchResultImpl.builder()
                    .entityId(entity.getId())
                    .entityType(EntityType.FIELD)
                    .repositoryId(entity.getRepositoryId())
                    .fullyQualifiedName(entity.getName())
                    .filePath(entity.getFilePath())
                    .content(entity.getName() + ": " + (entity.getSourceCode() != null ? entity.getSourceCode() : ""))
                    .score(score)
                    .searchMode(SearchMode.SEMANTIC)
                    .build());
        }

        log.debug("🔍 [SEARCH FIELDS] Found {} field results", results.size());
        return results;
    }

    private static String getCypher(List<String> repositoryIds, List<String> keywords) {
        StringBuilder whereClause = new StringBuilder("WHERE (");
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) whereClause.append(" OR ");
            whereClause.append(String.format(
                    "toLower(e.name) CONTAINS toLower($kw%d) OR " +
                            "toLower(e.fullyQualifiedName) CONTAINS toLower($kw%d) OR " +
                            "toLower(e.sourceCode) CONTAINS toLower($kw%d) OR " +
                            "toLower(e.filePath) CONTAINS toLower($kw%d)",
                    i, i, i, i
            ));
        }
        whereClause.append(")");

        if (repositoryIds != null && !repositoryIds.isEmpty()) {
            whereClause.append(" AND e.repositoryId IN $repoIds");
        }

        return String.format("MATCH (e:Entity) %s RETURN e LIMIT $limit", whereClause);
    }

    @Override
    public List<SearchResult> temporalSearch(String query, List<String> repositoryIds) {
        log.warn("Temporal search not yet implemented, returning empty results");
        return new ArrayList<>();
    }

    @Override
    public DependencyResult findDependencies(String entityId, int depth, DependencyDirection direction) {
        var relDirection = switch (direction) {
            case INCOMING -> RelationshipDirection.INCOMING;
            case OUTGOING -> RelationshipDirection.OUTGOING;
            case BOTH -> RelationshipDirection.BOTH;
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
        String cypher = "MATCH path = shortestPath((from:Entity {id: $fromId})-[*..5]-(to:Entity {id: $toId})) RETURN path";
        var results = graphStore.executeCypherQueryRaw(cypher, Map.of("fromId", fromEntityId, "toId", toEntityId));
        List<String> pathNodes = new ArrayList<>(List.of(fromEntityId, toEntityId));
        String explanation = results.isEmpty() ? "No direct relationship found" : "Connected via " + results.size() + " path(s)";
        return new DefaultRelationshipExplanation(fromEntityId, toEntityId, pathNodes, explanation);
    }

    private SearchMode detectBestSearchMode(String query) {
        String lower = query.toLowerCase();
        if (lower.contains("call") || lower.contains("depend") || lower.contains("extend") || lower.contains("implement")) return SearchMode.STRUCTURAL;
        if (lower.contains("change") || lower.contains("history") || lower.contains("recent")) return SearchMode.TEMPORAL;
        // DEFAULT: Use HYBRID (exact match first, fuzzy fallback) - Gemini's recommendation
        return SearchMode.HYBRID;
    }

    private String buildStructuralQuery(String query, List<String> repositoryIds) {
        String base = "MATCH (e:Entity) WHERE ";
        List<String> conditions = new ArrayList<>();
        String[] words = query.split("\\s+");
        for (String word : words) {
            if (word.length() > 2 && Character.isUpperCase(word.charAt(0))) conditions.add("e.name CONTAINS '" + word + "'");
        }
        if (conditions.isEmpty()) conditions.add("e.name CONTAINS '" + query + "'");
        if (repositoryIds != null && !repositoryIds.isEmpty()) conditions.add("e.repositoryId IN $repoIds");
        return base + String.join(" OR ", conditions) + " RETURN e LIMIT 20";
    }

    /**
     * HYBRID SEARCH STRATEGY (Gemini's recommendation):
     * 1. Try exact match first (cheap, fast)
     * 2. If no results, fall back to fuzzy/semantic search
     *
     * This prevents expensive CONTAINS queries when exact match exists.
     */
    private List<SearchResult> hybridSearch(String query, SearchOptions options) {
        log.info("🔍 [HYBRID SEARCH] Starting hybrid search for: '{}'", query);
        List<String> repositoryIds = options != null ? options.getRepositoryIds() : null;

        // STEP 1: Try exact match (fast, index-backed)
        List<SearchResult> exactMatches = exactMatchSearch(query, repositoryIds);

        if (!exactMatches.isEmpty()) {
            log.info("✅ [HYBRID SEARCH] Found {} exact matches - skipping fuzzy search", exactMatches.size());
            return exactMatches;
        }

        // STEP 2: No exact match - fall back to fuzzy semantic search
        log.info("⚠️  [HYBRID SEARCH] No exact matches - falling back to fuzzy/semantic search");
        List<SearchResult> structural = structuralSearch(query, repositoryIds);
        List<SearchResult> semantic = semanticSearch(query, repositoryIds, 10);

        // Merge and deduplicate
        Map<String, SearchResult> merged = new HashMap<>();
        for (var result : structural) merged.put(result.getEntityId(), result);
        for (var result : semantic) merged.putIfAbsent(result.getEntityId(), result);

        List<SearchResult> results = new ArrayList<>(merged.values());
        log.info("🔍 [HYBRID SEARCH] Total results: {} (structural: {}, semantic: {})",
            results.size(), structural.size(), semantic.size());

        return results;
    }

    /**
     * EXACT MATCH SEARCH - Fast, index-backed queries.
     * Searches for entities where name exactly matches the query (case-insensitive).
     *
     * Example: "ChatController" finds Type with name="ChatController"
     * This is MUCH faster than CONTAINS and uses Neo4j indexes.
     */
    private List<SearchResult> exactMatchSearch(String query, List<String> repositoryIds) {
        log.debug("🔍 [EXACT MATCH] Searching for exact match: '{}'", query);

        List<SearchResult> results = new ArrayList<>();
        String cleanQuery = query.trim();

        // Try exact match on Type nodes (classes, interfaces, enums)
        String typeCypher = """
            MATCH (t:Type)
            WHERE toLower(t.name) = toLower($query)
            %s
            RETURN t
            LIMIT 10
            """.formatted(repositoryIds != null && !repositoryIds.isEmpty()
                ? "AND t.repositoryId IN $repoIds" : "");

        Map<String, Object> params = new HashMap<>();
        params.put("query", cleanQuery);
        if (repositoryIds != null && !repositoryIds.isEmpty()) {
            params.put("repoIds", repositoryIds);
        }

        var typeEntities = graphStore.executeCypherQuery(typeCypher, params);
        for (var entity : typeEntities) {
            results.add(SearchResultImpl.builder()
                .entityId(entity.getId())
                .entityType(EntityType.CLASS)
                .repositoryId(entity.getRepositoryId())
                .fullyQualifiedName(entity.getFullyQualifiedName() != null ? entity.getFullyQualifiedName() : entity.getName())
                .filePath(entity.getFilePath())
                .content(entity.getSourceCode())
                .score(1.0f) // Perfect match
                .searchMode(SearchMode.HYBRID)
                .build());
        }

        // Also try exact match on Method nodes
        String methodCypher = """
            MATCH (m:Method)
            WHERE toLower(m.name) = toLower($query)
            %s
            RETURN m
            LIMIT 5
            """.formatted(repositoryIds != null && !repositoryIds.isEmpty()
                ? "AND m.repositoryId IN $repoIds" : "");

        var methodEntities = graphStore.executeCypherQuery(methodCypher, params);
        for (var entity : methodEntities) {
            results.add(SearchResultImpl.builder()
                .entityId(entity.getId())
                .entityType(EntityType.METHOD)
                .repositoryId(entity.getRepositoryId())
                .fullyQualifiedName(entity.getName())
                .filePath(entity.getFilePath())
                .content(entity.getSourceCode())
                .score(0.95f) // Slightly lower than class matches
                .searchMode(SearchMode.HYBRID)
                .build());
        }

        log.debug("🔍 [EXACT MATCH] Found {} exact matches", results.size());
        return results;
    }

    private List<String> tokenizeQuery(String query) {
        if (query == null || query.isBlank()) return List.of();
        List<String> stopWords = List.of("the", "a", "an", "and", "or", "is", "are", "what", "how", "why");
        String[] words = query.toLowerCase().trim().split("\\s+");
        List<String> keywords = new ArrayList<>();
        for (String word : words) {
            word = word.replaceAll("[^a-z0-9.\\-_/]", "");
            if (!word.isEmpty() && word.length() >= 2 && !stopWords.contains(word)) keywords.add(word);
        }
        return keywords;
    }

    private boolean isPathLike(String query) {
        return query != null && (query.contains(".") || query.contains("/"));
    }

    private float calculateKeywordScore(com.purchasingpower.autoflow.core.CodeEntity entity, List<String> keywords) {
        int matches = 0;
        String name = entity.getName() != null ? entity.getName().toLowerCase() : "";
        String path = entity.getFilePath() != null ? entity.getFilePath().toLowerCase() : "";
        for (String keyword : keywords) {
            if (name.contains(keyword)) matches += 3;
            if (path.contains(keyword)) matches += 3;
        }
        return Math.min(1.0f, matches / (keywords.size() * 3.0f));
    }
}