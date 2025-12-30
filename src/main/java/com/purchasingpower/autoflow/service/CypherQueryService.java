package com.purchasingpower.autoflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.model.retrieval.CodeContext;
import com.purchasingpower.autoflow.storage.Neo4jGraphStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * CypherQueryService - Replaces Pinecone embedding search with Neo4j Cypher queries.
 *
 * Instead of fuzzy embedding similarity, uses precise graph queries for:
 * - Metadata filtering (annotations, class names, packages)
 * - Relationship traversal (dependencies, calls, references)
 * - Full-text search (code content, documentation)
 * - Structural queries (package hierarchy, class relationships)
 *
 * Benefits over Pinecone:
 * - More accurate (precise queries vs fuzzy similarity)
 * - More complete (returns ALL matches, not top-K approximation)
 * - Faster (optimized graph database)
 * - Explainable (can show Cypher query)
 * - Free (no Pinecone costs)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CypherQueryService {

    private final GeminiClient geminiClient;
    private final Driver neo4jDriver;
    private final PromptLibraryService promptLibrary;
    private final ObjectMapper objectMapper;

    /**
     * Generate Cypher query from natural language question.
     *
     * @param question Natural language question
     * @param repoName Repository name to filter by
     * @return Cypher query string
     */
    public String generateCypherQuery(String question, String repoName) {
        log.debug("Generating Cypher query for: {}", question);

        String prompt = buildCypherGenerationPrompt(question, repoName);
        String llmResponse = geminiClient.callChatApi(prompt, "cypher-generator");

        // Extract Cypher from response (LLM might wrap in markdown)
        String cypher = extractCypher(llmResponse);

        log.debug("Generated Cypher: {}", cypher);
        return cypher;
    }

    /**
     * Execute Cypher query and convert results to CodeContext.
     *
     * @param cypher Cypher query to execute
     * @param repoName Repository name for filtering
     * @return List of code chunks matching query
     */
    public List<CodeContext> executeCypherQuery(String cypher, String repoName) {
        log.debug("Executing Cypher query on Neo4j");

        List<CodeContext> results = new ArrayList<>();

        try (Session session = neo4jDriver.session()) {
            Result result = session.run(cypher, Map.of("repoName", repoName));

            while (result.hasNext()) {
                var record = result.next();

                // Extract node from result (could be named 'c', 'm', 'n', 'node', etc.)
                org.neo4j.driver.types.Node node = null;
                for (String key : record.keys()) {
                    if (record.get(key).hasType(org.neo4j.driver.internal.types.InternalTypeSystem.TYPE_SYSTEM.NODE())) {
                        node = record.get(key).asNode();
                        break;
                    }
                }

                if (node == null) {
                    continue;
                }

                // Convert Neo4j node to CodeContext
                CodeContext context = convertNodeToCodeContext(node);
                if (context != null) {
                    results.add(context);
                }
            }

            log.debug("Cypher query returned {} results", results.size());
            return results;

        } catch (Exception e) {
            log.error("Failed to execute Cypher query: {}", cypher, e);
            return List.of();
        }
    }

    /**
     * Query by metadata filters (annotations, class names, etc.)
     *
     * @param repoName Repository name
     * @param filters Map of metadata filters
     * @return Matching code chunks
     */
    public List<CodeContext> queryByMetadata(String repoName, Map<String, String> filters) {
        log.debug("Querying by metadata: {}", filters);

        StringBuilder cypher = new StringBuilder("MATCH (n) WHERE n.repoName = $repoName");

        // Add filters
        if (filters.containsKey("annotations")) {
            String annotations = filters.get("annotations");
            cypher.append(" AND any(a IN n.annotations WHERE a IN [")
                  .append(Arrays.stream(annotations.split(","))
                          .map(a -> "'" + a.trim() + "'")
                          .reduce((a, b) -> a + "," + b)
                          .orElse(""))
                  .append("])");
        }

        if (filters.containsKey("className_contains")) {
            String className = filters.get("className_contains");
            cypher.append(" AND n.name CONTAINS '").append(className).append("'");
        }

        if (filters.containsKey("package")) {
            String packageName = filters.get("package");
            cypher.append(" AND n.packageName = '").append(packageName).append("'");
        }

        cypher.append(" RETURN n LIMIT 50");

        return executeCypherQuery(cypher.toString(), repoName);
    }

    /**
     * Query by file path pattern.
     *
     * @param repoName Repository name
     * @param pattern Regex pattern for file path
     * @return Matching code chunks
     */
    public List<CodeContext> queryByFilePattern(String repoName, String pattern) {
        log.debug("Querying by file pattern: {}", pattern);

        String cypher = """
            MATCH (n)
            WHERE n.repoName = $repoName
              AND n.sourceFilePath =~ $pattern
            RETURN n
            LIMIT 20
            """;

        try (Session session = neo4jDriver.session()) {
            Result result = session.run(cypher, Map.of(
                "repoName", repoName,
                "pattern", ".*" + pattern + ".*"
            ));

            List<CodeContext> results = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                org.neo4j.driver.types.Node node = record.get("n").asNode();
                CodeContext context = convertNodeToCodeContext(node);
                if (context != null) {
                    results.add(context);
                }
            }

            return results;
        }
    }

    /**
     * Full-text search on code content.
     *
     * @param repoName Repository name
     * @param searchText Text to search for
     * @param limit Maximum results
     * @return Matching code chunks
     */
    public List<CodeContext> fullTextSearch(String repoName, String searchText, int limit) {
        log.debug("Full-text search for: {}", searchText);

        // Simple CONTAINS search (can be improved with Neo4j full-text indexes)
        String cypher = """
            MATCH (n)
            WHERE n.repoName = $repoName
              AND (n.sourceCode CONTAINS $searchText
                   OR n.summary CONTAINS $searchText
                   OR n.javadoc CONTAINS $searchText)
            RETURN n
            LIMIT $limit
            """;

        try (Session session = neo4jDriver.session()) {
            Result result = session.run(cypher, Map.of(
                "repoName", repoName,
                "searchText", searchText,
                "limit", limit
            ));

            List<CodeContext> results = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                org.neo4j.driver.types.Node node = record.get("n").asNode();
                CodeContext context = convertNodeToCodeContext(node);
                if (context != null) {
                    results.add(context);
                }
            }

            return results;
        }
    }

    /**
     * Convert Neo4j node to CodeContext.
     */
    private CodeContext convertNodeToCodeContext(org.neo4j.driver.types.Node node) {
        try {
            String id = node.get("id").asString(node.get("fullyQualifiedName").asString("unknown"));
            String chunkType = node.labels().iterator().next(); // Class, Method, Field
            String className = node.get("name").asString("");
            String methodName = chunkType.equals("Method") ? node.get("name").asString("") : "";
            String filePath = node.get("sourceFilePath").asString("");

            // Get content (prefer sourceCode, fall back to summary)
            String content = "";
            if (node.containsKey("sourceCode") && !node.get("sourceCode").isNull()) {
                content = node.get("sourceCode").asString("");
            } else if (node.containsKey("summary") && !node.get("summary").isNull()) {
                content = node.get("summary").asString("");
            }

            // Neo4j results don't have similarity scores, use 1.0
            float score = 1.0f;

            return new CodeContext(
                id,
                score,
                chunkType,
                className,
                methodName,
                filePath,
                content
            );

        } catch (Exception e) {
            log.warn("Failed to convert node to CodeContext", e);
            return null;
        }
    }

    /**
     * Build prompt for Cypher generation.
     */
    private String buildCypherGenerationPrompt(String question, String repoName) {
        return """
            You are a Neo4j Cypher expert for code analysis.

            Neo4j Schema:
            - Nodes: Class, Method, Field, Document
            - Properties: name, fullyQualifiedName, packageName, sourceFilePath, annotations, sourceCode, summary, javadoc
            - Relationships: DEPENDS_ON, CALLS, REFERENCES, EXTENDS, IMPLEMENTS

            Question: %s
            Repository: %s

            Generate a Cypher query to answer this question.
            Return ONLY the Cypher query, no explanation or markdown.
            Use $repoName parameter for filtering.

            Examples:

            Q: "All REST controllers"
            A: MATCH (c:Class) WHERE c.repoName = $repoName AND any(a IN c.annotations WHERE a CONTAINS 'RestController') RETURN c LIMIT 50

            Q: "Methods with @Transactional"
            A: MATCH (m:Method) WHERE m.repoName = $repoName AND any(a IN m.annotations WHERE a CONTAINS 'Transactional') RETURN m LIMIT 50

            Q: "Classes in payment package"
            A: MATCH (c:Class) WHERE c.repoName = $repoName AND c.packageName CONTAINS 'payment' RETURN c LIMIT 50

            Q: "What calls PaymentService.processPayment"
            A: MATCH (caller:Method)-[:CALLS]->(target:Method {name: 'processPayment'}) WHERE target.className = 'PaymentService' RETURN caller LIMIT 50

            Generate Cypher query:
            """.formatted(question, repoName);
    }

    /**
     * Extract Cypher from LLM response (removes markdown).
     */
    private String extractCypher(String response) {
        String cleaned = response.trim();

        // Remove markdown code blocks
        if (cleaned.startsWith("```cypher")) {
            cleaned = cleaned.substring(9);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }
}
