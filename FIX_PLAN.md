# Fix Plan for Graph Search Issues

## Critical Bugs Found

### Bug #1: Property Name Mismatch (CRITICAL)
**File:** `Neo4jGraphStoreImpl.java:428`
**Issue:** Reading `fullyQualifiedName` but storing as `fqn`

```java
// BEFORE (line 428)
.fullyQualifiedName(getStringValue(node, "fullyQualifiedName"))

// AFTER
.fullyQualifiedName(getStringValue(node, "fqn"))  // Match the stored property name
```

### Bug #2: Empty String Defaults Break Queries
**File:** `Neo4jGraphStoreImpl.java:439`
**Issue:** Returns `""` for missing properties, which breaks `CONTAINS` queries

```java
// BEFORE
private String getStringValue(Node node, String key) {
    return node.containsKey(key) && !node.get(key).isNull() ? node.get(key).asString() : "";
}

// AFTER
private String getStringValue(Node node, String key) {
    return node.containsKey(key) && !node.get(key).isNull() ? node.get(key).asString() : null;
}
```

**Then update SearchServiceImpl to handle NULL:**

```java
// In searchTypes method (line 159-168)
WHERE (
  (e.name IS NOT NULL AND toLower(e.name) CONTAINS toLower($kw0)) OR
  (e.fqn IS NOT NULL AND toLower(e.fqn) CONTAINS toLower($kw0)) OR
  (e.sourceCode IS NOT NULL AND toLower(e.sourceCode) CONTAINS toLower($kw0)) OR
  (e.filePath IS NOT NULL AND toLower(e.filePath) CONTAINS toLower($kw0))
)
```

### Bug #3: includeMethods Defaults to False
**File:** `SemanticSearchTool.java:74-76`
**Issue:** Methods are never included in semantic search

```java
// BEFORE
boolean includeMethods = parameters.containsKey("include_methods")
    ? (Boolean) parameters.get("include_methods")
    : false;  // <-- Default is false

// AFTER - Option 1: Change default
boolean includeMethods = parameters.containsKey("include_methods")
    ? (Boolean) parameters.get("include_methods")
    : true;  // Always include methods by default

// AFTER - Option 2: Update the prompt/tool description
// Update getDescription() to make it clear methods are opt-in
```

### Bug #4: Vector Search Doesn't Return sourceCode
**File:** `SemanticSearchTool.java:139-145`
**Issue:** LLM receives only descriptions, not actual source code

```java
// BEFORE (line 139-145)
RETURN node.fqn as fqn,
       node.name as name,
       node.packageName as package,
       node.kind as kind,
       node.description as description,
       node.filePath as filePath,
       score

// AFTER - Add sourceCode
RETURN node.fqn as fqn,
       node.name as name,
       node.packageName as package,
       node.kind as kind,
       node.description as description,
       node.sourceCode as sourceCode,  // <-- ADD THIS
       node.filePath as filePath,
       score
```

**Also update method search (line 177-180):**

```java
RETURN node.name as name,
       node.signature as signature,
       node.description as description,
       node.sourceCode as sourceCode,  // <-- ADD THIS
       node.returnType as returnType,
       score
```

### Bug #5: No Similarity Score Filtering
**File:** `SemanticSearchTool.java:138`
**Issue:** Returns low-quality matches

```java
// BEFORE
WHERE node.repositoryId IN $repoIds

// AFTER
WHERE node.repositoryId IN $repoIds
  AND score > $minScore  // Add minimum threshold
```

**Add minScore parameter:**

```java
Map.of(
    "queryEmbedding", queryEmbedding,
    "repoIds", repositoryIds,
    "limit", limit * 2,
    "minScore", 0.65  // Require at least 65% similarity
)
```

## Testing Plan

After applying fixes, test with:

```bash
# 1. Restart application
mvn spring-boot:run

# 2. Test search for ChatController
curl -X POST http://localhost:8080/api/search \
  -H "Content-Type: application/json" \
  -d '{"query": "ChatController", "repositoryUrl": "https://github.com/ssharma1011/rag-orchestrator"}'

# Expected: Should return ChatController with full source code
```

## Neo4j Verification Queries

Run these in Neo4j Browser to verify data integrity:

```cypher
// 1. Check property names on Type nodes
MATCH (t:Type {name: 'ChatController'})
RETURN keys(t) as properties;
// Expected: Should include "fqn", "name", "sourceCode", "filePath"

// 2. Verify the exact query from logs
MATCH (e:Type)
WHERE (toLower(e.name) CONTAINS 'chatcontroller'
    OR toLower(e.fqn) CONTAINS 'chatcontroller')
AND e.repositoryId = 'd4aa6f8a-f69a-48d6-aed1-063091eb9da4'
RETURN e.name, e.fqn, length(e.sourceCode) as codeLength
LIMIT 5;
// Expected: Should return ChatController with non-zero codeLength

// 3. Check method relationships
MATCH (t:Type {name: 'ChatController'})-[:DECLARES]->(m:Method)
RETURN t.name, count(m) as methodCount, collect(m.name)[0..5] as sampleMethods;
// Expected: Should show methods like "chat", "getConversation", etc.
```

## Priority Order

1. **Fix Bug #1 (property mismatch)** - Most critical, prevents ALL searches
2. **Fix Bug #2 (null handling)** - Prevents CONTAINS queries from working
3. **Fix Bug #3 (include methods)** - Improves semantic search quality
4. **Fix Bug #4 (add sourceCode)** - Enables LLM to actually explain code
5. **Fix Bug #5 (score filtering)** - Improves result quality

Apply fixes in this order and test after each one.
