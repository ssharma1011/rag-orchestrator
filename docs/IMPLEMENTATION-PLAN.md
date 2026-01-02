# Implementation Plan: Neo4j Vector Search Integration

**Phase:** 1A - Fix Semantic Search
**Estimated Effort:** 3-5 days
**Priority:** Critical

---

## Overview

This plan adds true semantic search capability using Neo4j's native vector search, replacing the current CONTAINS-based "semantic search" that doesn't work.

---

## Step 1: Add Embedding Property to Graph Nodes (Day 1)

### 1.1 Update Neo4j Schema

Add `embedding` property to Class and Method nodes during indexing.

**File:** `src/main/java/com/purchasingpower/autoflow/storage/Neo4jGraphStore.java`

```java
// Add to storeClassNode method
public void storeClassNode(ClassNode classNode, List<Double> embedding) {
    String cypher = """
        MERGE (c:Class {fullyQualifiedName: $fqn})
        SET c.name = $name,
            c.packageName = $packageName,
            c.sourceCode = $sourceCode,
            c.summary = $summary,
            c.embedding = $embedding,
            c.repoName = $repoName
        """;

    session.run(cypher, Map.of(
        "fqn", classNode.getFullyQualifiedName(),
        "name", classNode.getName(),
        "embedding", embedding,  // NEW: vector embedding
        // ... other params
    ));
}
```

### 1.2 Create Vector Index

**File:** `src/main/resources/db/neo4j-vector-index.cypher`

```cypher
// Create vector index for Class nodes
CREATE VECTOR INDEX class_embedding_index IF NOT EXISTS
FOR (c:Class) ON c.embedding
OPTIONS {
    indexConfig: {
        `vector.dimensions`: 768,
        `vector.similarity_function`: 'cosine'
    }
}

// Create vector index for Method nodes
CREATE VECTOR INDEX method_embedding_index IF NOT EXISTS
FOR (m:Method) ON m.embedding
OPTIONS {
    indexConfig: {
        `vector.dimensions`: 768,
        `vector.similarity_function`: 'cosine'
    }
}
```

---

## Step 2: Create Enriched Representation Service (Day 1-2)

### 2.1 New Service: EnrichedEmbeddingService

**File:** `src/main/java/com/purchasingpower/autoflow/service/EnrichedEmbeddingService.java`

```java
package com.purchasingpower.autoflow.service;

import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.model.neo4j.ClassNode;
import com.purchasingpower.autoflow.model.neo4j.MethodNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Creates enriched text representations for embedding.
 *
 * Instead of embedding raw code, we create semantic descriptions
 * that capture the PURPOSE and CONTEXT of code elements.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrichedEmbeddingService {

    private final GeminiClient geminiClient;

    /**
     * Create enriched representation for a class.
     *
     * Includes: name, package, purpose, annotations, key methods, dependencies
     */
    public String createClassRepresentation(ClassNode classNode) {
        StringBuilder repr = new StringBuilder();

        repr.append("Class: ").append(classNode.getName()).append("\n");
        repr.append("Package: ").append(classNode.getPackageName()).append("\n");

        // Purpose from javadoc or inferred summary
        if (classNode.getJavadoc() != null && !classNode.getJavadoc().isEmpty()) {
            repr.append("Purpose: ").append(classNode.getJavadoc()).append("\n");
        } else if (classNode.getSummary() != null) {
            repr.append("Purpose: ").append(classNode.getSummary()).append("\n");
        }

        // Annotations (semantic markers)
        if (classNode.getAnnotations() != null && !classNode.getAnnotations().isEmpty()) {
            repr.append("Type: ").append(inferTypeFromAnnotations(classNode.getAnnotations())).append("\n");
            repr.append("Annotations: ").append(String.join(", ", classNode.getAnnotations())).append("\n");
        }

        // Superclass and interfaces
        if (classNode.getSuperClassName() != null) {
            repr.append("Extends: ").append(classNode.getSuperClassName()).append("\n");
        }
        if (classNode.getInterfaces() != null && !classNode.getInterfaces().isEmpty()) {
            repr.append("Implements: ").append(String.join(", ", classNode.getInterfaces())).append("\n");
        }

        // Key method signatures (gives semantic clues)
        List<String> methodSignatures = classNode.getMethods().stream()
            .map(m -> m.getName() + "(" + m.getParameterTypes() + ")")
            .limit(10)
            .collect(Collectors.toList());
        if (!methodSignatures.isEmpty()) {
            repr.append("Key Methods: ").append(String.join(", ", methodSignatures)).append("\n");
        }

        return repr.toString();
    }

    /**
     * Create enriched representation for a method.
     */
    public String createMethodRepresentation(MethodNode method, String className) {
        StringBuilder repr = new StringBuilder();

        repr.append("Method: ").append(method.getName()).append("\n");
        repr.append("Class: ").append(className).append("\n");
        repr.append("Signature: ").append(method.getReturnType())
            .append(" ").append(method.getName())
            .append("(").append(method.getParameters()).append(")\n");

        if (method.getJavadoc() != null && !method.getJavadoc().isEmpty()) {
            repr.append("Purpose: ").append(method.getJavadoc()).append("\n");
        }

        if (method.getAnnotations() != null && !method.getAnnotations().isEmpty()) {
            repr.append("Annotations: ").append(String.join(", ", method.getAnnotations())).append("\n");
        }

        // Include a semantic summary of what the method does
        // This helps with queries like "find error handling code"
        repr.append("Implementation: ").append(summarizeMethod(method)).append("\n");

        return repr.toString();
    }

    /**
     * Generate embedding for text.
     */
    public List<Double> createEmbedding(String text) {
        return geminiClient.createEmbedding(text);
    }

    /**
     * Batch generate embeddings.
     */
    public List<List<Double>> batchCreateEmbeddings(List<String> texts) {
        return geminiClient.batchCreateEmbeddings(texts);
    }

    private String inferTypeFromAnnotations(List<String> annotations) {
        for (String ann : annotations) {
            if (ann.contains("RestController") || ann.contains("Controller")) return "REST Controller";
            if (ann.contains("Service")) return "Service";
            if (ann.contains("Repository")) return "Repository";
            if (ann.contains("Component")) return "Component";
            if (ann.contains("Configuration")) return "Configuration";
            if (ann.contains("Entity")) return "Entity";
        }
        return "Class";
    }

    private String summarizeMethod(MethodNode method) {
        String code = method.getSourceCode();
        if (code == null || code.isEmpty()) return "";

        // Quick heuristic summaries based on code patterns
        StringBuilder summary = new StringBuilder();

        if (code.contains("try") && code.contains("catch")) {
            summary.append("Has error handling. ");
        }
        if (code.contains("@Transactional") || code.contains("transaction")) {
            summary.append("Transactional operation. ");
        }
        if (code.contains("repository.") || code.contains("Repository.")) {
            summary.append("Database operation. ");
        }
        if (code.contains("http") || code.contains("webClient") || code.contains("restTemplate")) {
            summary.append("External API call. ");
        }
        if (code.contains("log.") || code.contains("logger.")) {
            summary.append("Has logging. ");
        }

        return summary.toString().trim();
    }
}
```

---

## Step 3: Update CodeIndexerAgent (Day 2)

### 3.1 Add Embedding Generation to Indexing Flow

**File:** `src/main/java/com/purchasingpower/autoflow/workflow/agents/CodeIndexerAgent.java`

```java
// Add to existing class
private final EnrichedEmbeddingService embeddingService;

// Update indexRepository method to generate embeddings
private void indexClassWithEmbedding(ClassNode classNode) {
    // Create enriched representation
    String enrichedText = embeddingService.createClassRepresentation(classNode);

    // Generate embedding
    List<Double> embedding = embeddingService.createEmbedding(enrichedText);

    // Store with embedding
    neo4jGraphStore.storeClassNode(classNode, embedding);

    log.debug("Indexed {} with embedding (dim: {})",
        classNode.getName(), embedding.size());
}
```

---

## Step 4: Implement Vector Search in CypherQueryService (Day 2-3)

### 4.1 Add Vector Search Method

**File:** `src/main/java/com/purchasingpower/autoflow/service/CypherQueryService.java`

```java
/**
 * Semantic search using Neo4j vector similarity.
 *
 * This is TRUE semantic search - finds conceptually similar code
 * even when terminology differs.
 *
 * @param repoName Repository to search
 * @param query Natural language query
 * @param topK Number of results
 * @return Semantically similar code chunks
 */
public List<CodeContext> vectorSearch(String repoName, String query, int topK) {
    log.debug("Vector search for: {}", query);

    // Generate query embedding
    List<Double> queryEmbedding = embeddingService.createEmbedding(query);

    // Neo4j vector similarity query
    String cypher = """
        CALL db.index.vector.queryNodes('class_embedding_index', $topK, $queryEmbedding)
        YIELD node, score
        WHERE node.repoName = $repoName
        RETURN node, score
        ORDER BY score DESC
        """;

    try (Session session = neo4jDriver.session()) {
        Result result = session.run(cypher, Map.of(
            "repoName", repoName,
            "topK", topK,
            "queryEmbedding", queryEmbedding
        ));

        List<CodeContext> results = new ArrayList<>();
        while (result.hasNext()) {
            var record = result.next();
            org.neo4j.driver.types.Node node = record.get("node").asNode();
            double score = record.get("score").asDouble();

            CodeContext context = convertNodeToCodeContext(node, (float) score);
            if (context != null) {
                results.add(context);
            }
        }

        log.debug("Vector search returned {} results", results.size());
        return results;
    }
}

/**
 * Hybrid search: combines structural graph query with semantic similarity.
 */
public List<CodeContext> hybridSearch(String repoName, String query, int topK) {
    // Step 1: Get candidates from vector search
    List<CodeContext> vectorResults = vectorSearch(repoName, query, topK * 2);

    // Step 2: Enrich with graph relationships
    for (CodeContext ctx : vectorResults) {
        // Add dependency context
        List<String> dependencies = getDependencies(ctx.id(), repoName);
        List<String> dependents = getDependents(ctx.id(), repoName);
        // Could add to context metadata
    }

    // Step 3: Re-score based on combined relevance
    return vectorResults.stream()
        .sorted((a, b) -> Float.compare(b.score(), a.score()))
        .limit(topK)
        .collect(Collectors.toList());
}
```

---

## Step 5: Update DynamicRetrievalExecutor (Day 3)

### 5.1 Fix executeSemanticSearch to Use Vector Search

**File:** `src/main/java/com/purchasingpower/autoflow/service/DynamicRetrievalExecutor.java`

```java
/**
 * Execute semantic_search strategy.
 *
 * ✅ NOW USES TRUE VECTOR SEARCH (not CONTAINS keyword matching)
 */
private List<CodeContext> executeSemanticSearch(RetrievalStrategy strategy) {
    String query = strategy.getRequiredParameter("query");
    Integer topK = strategy.getParameter("top_k", 20);

    String repoName = strategy.getTargetRepos().isEmpty()
        ? null : strategy.getTargetRepos().get(0);

    // ✅ Use Neo4j vector search (TRUE semantic search)
    return cypherQueryService.vectorSearch(repoName, query, topK);
}
```

---

## Step 6: Add Query Type Classification (Day 3-4)

### 6.1 New Service: QueryClassifier

**File:** `src/main/java/com/purchasingpower/autoflow/service/QueryClassifier.java`

```java
package com.purchasingpower.autoflow.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Classifies queries to route to appropriate retrieval strategy.
 */
@Service
@RequiredArgsConstructor
public class QueryClassifier {

    public enum QueryType {
        STRUCTURAL,  // "What depends on X?", "What calls Y?"
        SEMANTIC,    // "How does authentication work?", "Find payment logic"
        HYBRID       // Complex queries needing both
    }

    /**
     * Classify query type based on patterns.
     */
    public QueryType classify(String query) {
        String lower = query.toLowerCase();

        // Structural indicators
        if (lower.contains("depends on") ||
            lower.contains("calls") ||
            lower.contains("references") ||
            lower.contains("what uses") ||
            lower.contains("what calls") ||
            lower.contains("show dependencies") ||
            lower.matches(".*@\\w+.*")) {  // Annotation queries
            return QueryType.STRUCTURAL;
        }

        // Semantic indicators
        if (lower.contains("how does") ||
            lower.contains("explain") ||
            lower.contains("understand") ||
            lower.contains("related to") ||
            lower.contains("find code") ||
            lower.contains("where is") ||
            lower.contains("show me")) {
            return QueryType.SEMANTIC;
        }

        // Default to hybrid for complex queries
        return QueryType.HYBRID;
    }
}
```

---

## Step 7: Configuration Updates (Day 4)

### 7.1 Add Vector Search Configuration

**File:** `src/main/resources/application.yml`

```yaml
app:
  vector-search:
    enabled: true
    index-name: "class_embedding_index"
    dimensions: 768  # Gemini embedding dimensions
    similarity-function: cosine
    min-score: 0.7   # Minimum similarity threshold
    default-top-k: 20

  gemini:
    embedding-model: text-embedding-004
    embedding-batch-size: 100
```

---

## Step 8: Update Prompts (Day 4)

### 8.1 Fix Misleading Documentation

**File:** `src/main/resources/prompts/retrieval-planner.yaml`

```yaml
  2. **semantic_search** - Vector embedding similarity search
     Parameters:
     - query: Natural language search query (conceptual, not keyword)
     - top_k: Number of results (default: 20)
     Example use: Finding code related to concepts even when terminology differs
     Note: Uses cosine similarity on enriched code representations

     Good queries:
     - "payment processing logic" (conceptual)
     - "error handling and retry mechanisms" (conceptual)
     - "user authentication flow" (conceptual)

     Bad queries (use metadata_filter instead):
     - "PaymentService" (specific class name)
     - "@Transactional methods" (annotation filter)
```

---

## Testing Plan

### Unit Tests

```java
@Test
void semanticSearch_findsSimilarConcepts() {
    // Given: Indexed code with PaymentProcessor class
    indexClass("PaymentProcessor", "Handles credit card transactions");

    // When: Search for conceptually related query
    List<CodeContext> results = cypherQueryService.vectorSearch(
        "test-repo",
        "credit card payment handling",  // Different words, same concept
        10
    );

    // Then: Should find PaymentProcessor
    assertThat(results).isNotEmpty();
    assertThat(results.get(0).className()).contains("Payment");
}

@Test
void semanticSearch_handlesSynonyms() {
    // Given: Code about "authentication"
    indexClass("AuthService", "Verifies user credentials");

    // When: Search using synonym
    List<CodeContext> results = cypherQueryService.vectorSearch(
        "test-repo",
        "login verification",  // Synonym for authentication
        10
    );

    // Then: Should still find AuthService
    assertThat(results).isNotEmpty();
}
```

---

## Rollback Plan

If issues arise:
1. Set `app.vector-search.enabled: false`
2. `executeSemanticSearch` falls back to `fullTextSearch`
3. No data loss - embeddings are additional properties

---

## Success Metrics

| Metric | Before | Target |
|--------|--------|--------|
| Semantic query accuracy | ~20% (keyword match) | >80% |
| "Find authentication code" | 0 results | Top 5 relevant |
| Average retrieval time | 50ms | <200ms (with embeddings) |
| User satisfaction | Low | High |

---

## Files Changed Summary

| File | Change Type |
|------|-------------|
| `Neo4jGraphStore.java` | Modified - add embedding storage |
| `CypherQueryService.java` | Modified - add vector search |
| `DynamicRetrievalExecutor.java` | Modified - use vector search |
| `CodeIndexerAgent.java` | Modified - generate embeddings |
| `EnrichedEmbeddingService.java` | New - enriched representations |
| `QueryClassifier.java` | New - query routing |
| `application.yml` | Modified - vector config |
| `retrieval-planner.yaml` | Modified - fix docs |
| `neo4j-vector-index.cypher` | New - index creation |
