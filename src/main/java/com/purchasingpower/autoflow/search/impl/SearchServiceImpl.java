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

        // Tokenize query into keywords (future-proof for hybrid search with embeddings)
        List<String> keywords = tokenizeQuery(query);
        log.debug("Tokenized query into keywords: {}", keywords);

        if (keywords.isEmpty()) {
            return List.of();
        }

        // Build WHERE clause with case-insensitive keyword matching
        StringBuilder whereClause = new StringBuilder("WHERE (");
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) whereClause.append(" OR ");
            String keyword = keywords.get(i);
            whereClause.append(String.format(
                "toLower(e.name) CONTAINS toLower($kw%d) OR " +
                "toLower(e.fullyQualifiedName) CONTAINS toLower($kw%d) OR " +
                "toLower(e.sourceCode) CONTAINS toLower($kw%d)",
                i, i, i
            ));
        }
        whereClause.append(")");

        if (repositoryIds != null && !repositoryIds.isEmpty()) {
            whereClause.append(" AND e.repositoryId IN $repoIds");
        }

        String cypher = String.format("""
            MATCH (e:Entity)
            %s
            RETURN e
            LIMIT $limit
            """, whereClause);

        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < keywords.size(); i++) {
            params.put("kw" + i, keywords.get(i));
        }
        params.put("limit", topK);
        if (repositoryIds != null && !repositoryIds.isEmpty()) {
            params.put("repoIds", repositoryIds);
        }

        var entities = graphStore.executeCypherQuery(cypher, params);

        List<SearchResult> results = new ArrayList<>();
        for (var entity : entities) {
            // Score by number of matching keywords
            float score = calculateKeywordScore(entity, keywords);
            results.add(SearchResultImpl.builder()
                .entityId(entity.getId())
                .entityType(entity.getType())
                .repositoryId(entity.getRepositoryId())
                .fullyQualifiedName(entity.getFullyQualifiedName())
                .filePath(entity.getFilePath())
                .content(entity.getSourceCode())
                .score(score)
                .searchMode(SearchMode.SEMANTIC)
                .build());
        }

        // Sort by score descending
        results.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));

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

    /**
     * Tokenize query into searchable keywords.
     * Removes stop words and splits on whitespace.
     */
    private List<String> tokenizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // Common stop words to filter out
        List<String> stopWords = List.of(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "this", "that", "these", "those",
            "is", "are", "was", "were", "be", "been", "being",
            "do", "does", "did", "have", "has", "had",
            "what", "which", "who", "where", "when", "why", "how"
        );

        String[] words = query.toLowerCase().trim().split("\\s+");
        List<String> keywords = new ArrayList<>();

        for (String word : words) {
            // Remove punctuation
            word = word.replaceAll("[^a-z0-9]", "");

            // Skip if empty, too short, or stop word
            if (!word.isEmpty() && word.length() > 2 && !stopWords.contains(word)) {
                keywords.add(word);
            }
        }

        return keywords;
    }

    /**
     * Calculate relevance score based on keyword matches.
     */
    private float calculateKeywordScore(com.purchasingpower.autoflow.core.CodeEntity entity, List<String> keywords) {
        if (keywords.isEmpty()) {
            return 0.5f;
        }

        int matches = 0;
        String name = entity.getName() != null ? entity.getName().toLowerCase() : "";
        String fqn = entity.getFullyQualifiedName() != null ? entity.getFullyQualifiedName().toLowerCase() : "";
        String source = entity.getSourceCode() != null ? entity.getSourceCode().toLowerCase() : "";

        for (String keyword : keywords) {
            if (name.contains(keyword)) {
                matches += 3; // Name match is most important
            } else if (fqn.contains(keyword)) {
                matches += 2; // FQN match is second
            } else if (source.contains(keyword)) {
                matches += 1; // Source code match is least important
            }
        }

        // Normalize score to 0-1 range
        float maxPossibleScore = keywords.size() * 3.0f;
        return Math.min(1.0f, matches / maxPossibleScore);
    }
}
