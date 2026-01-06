# Vector Search Debugging & Fixes

## Issues Identified

### 1. Silent File Parsing Failures
**Problem:** JavaParserServiceImpl catches all exceptions and silently skips files with only a WARNING log.
**Impact:** If embedding generation fails for critical files (like ChatController), they're skipped without clear visibility.
**Fix Applied:** ✅ Changed to ERROR logging with full stack traces and summary of failed files.

**File:** `JavaParserServiceImpl.java` lines 78-89
```java
// BEFORE: log.warn("⚠️  Skipping file {} due to error: {}", file.getName(), e.getMessage());
// AFTER: log.error("❌ Failed to parse/embed file {}: {}", file.getName(), e.getMessage(), e);
//        + List of all failed files at the end
```

### 2. No Visibility Into Indexing Status
**Problem:** Can't tell if re-indexing completed, which files were indexed, or if embeddings were generated.
**Fix:** Created comprehensive diagnostic script `diagnose_neo4j.cypher` with 10 tests.

### 3. Vector Index Syntax (Potential Issue)
**Current Implementation:**
```cypher
CREATE VECTOR INDEX type_embedding_index IF NOT EXISTS
FOR (t:Type) ON (t.embedding)
OPTIONS {indexConfig: {
  `vector.dimensions`: 1024,
  `vector.similarity_function`: 'cosine'
}}
```

**Status:** Syntax appears correct for Neo4j 5.x, but needs verification that indexes are working.

### 4. Empty Embeddings Stored in Neo4j
**Problem:** If embedding generation fails for some reason, `List.of()` (empty list) is stored in Neo4j.
**Impact:** Vector index may include nodes with empty embeddings, causing search failures.
**Current Code:** `Neo4jGraphStoreImpl.java:548`
```java
"embedding", javaClass.getEmbedding() != null ? javaClass.getEmbedding() : List.of()
```
**Recommendation:** Store `null` instead of empty list, or skip nodes without embeddings.

### 5. Agent Tool Routing Failure
**Problem:** Agent consistently chooses `search_code` (keyword) instead of `semantic_search` (vector) even for conceptual questions.
**Root Cause:** Weak LLM (qwen2.5-coder:7b) may not understand complex tool routing instructions.
**Fixes Attempted:**
- Updated AutoFlowAgent prompts with explicit guidance
- Added examples: "properties file → finds application.yml"
**Status:** Still failing - needs testing with stronger model (qwen2.5-coder:32b)

## What to Do Next

### Step 1: Run Diagnostic Script
```bash
# Open Neo4j Browser at http://localhost:7474
# Copy-paste queries from diagnose_neo4j.cypher one by one
# Document which tests PASS and which FAIL
```

### Step 2: Rebuild and Re-index
```bash
# Rebuild with improved logging
mvn clean install -DskipTests

# Clear Neo4j completely (including indexes)
# In Neo4j Browser:
MATCH (n) DETACH DELETE n;
DROP INDEX type_embedding_index IF EXISTS;
DROP INDEX method_embedding_index IF EXISTS;

# Restart application and trigger indexing
mvn spring-boot:run

# Watch logs for:
# - "Failed to parse/embed file" (should be ZERO)
# - "✅ Successfully parsed X/Y files" (X should equal Y)
# - "Generated embedding for" (should see many)
# - "Created vector index: type_embedding_index"
```

### Step 3: Verify Embeddings Were Stored
Run diagnostic queries from `diagnose_neo4j.cypher`:
- Test 1: Check totalTypes > 0
- Test 2: Check typesWithEmbeddings == totalTypes
- Test 3: Check dimensions == 1024
- Test 5: Verify ChatController exists with embedding

### Step 4: Test Vector Search
```cypher
// Manual test of vector search
MATCH (t:Type {name: 'ChatController'})
WHERE t.embedding IS NOT NULL AND size(t.embedding) = 1024
WITH t.embedding as queryEmbedding
CALL db.index.vector.queryNodes('type_embedding_index', 5, queryEmbedding)
YIELD node, score
RETURN node.name, score
ORDER BY score DESC;
```

**Expected:** Should return ChatController (score ~1.0) and similar classes.

### Step 5: Test Semantic Search via API
```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "find the properties file",
    "repoUrl": "C:\\Users\\ssharma\\personal\\rag-orchestrator"
  }'
```

**Expected Agent Behavior:**
1. Call `semantic_search` (not search_code)
2. Query: "properties file" or "configuration file"
3. Result: Should find `application.yml`, `application-dev.yml`

## Key Questions to Answer

1. **Do Type nodes exist at all?**
   - Run: `MATCH (t:Type) RETURN count(t)`
   - If 0: Indexing failed completely

2. **Do Type nodes have embeddings?**
   - Run: `MATCH (t:Type) WHERE t.embedding IS NOT NULL RETURN count(t)`
   - If 0: Embeddings not generated/stored

3. **Are embeddings the correct size?**
   - Run: `MATCH (t:Type) WHERE size(t.embedding) != 1024 RETURN t.name, size(t.embedding)`
   - If any results: Wrong model used or partial embeddings

4. **Does ChatController specifically exist?**
   - Run: `MATCH (t:Type {name: 'ChatController'}) RETURN t`
   - If 0: ChatController parsing failed

5. **Are vector indexes populated?**
   - Run: `SHOW INDEXES WHERE type = 'VECTOR'`
   - Check: state='ONLINE', populationPercent=100

## Expected Logs After Fixes

### During Indexing
```
📂 Parsing 150 Java files
📄 Parsing Java file: ChatController.java
🔵 [EMBEDDING REQUEST] Provider=Ollama, Model=mxbai-embed-large, TextLength=450
🟢 [EMBEDDING RESPONSE] Provider=Ollama, Dimensions=1024
🔷 Generated embedding for method: chat (1024 dimensions)
...
✅ Successfully parsed 150/150 files
📦 Storing JavaClass: com.purchasingpower.autoflow.api.ChatController
✅ Stored class with 5 methods, 3 fields
```

### If Files Fail (Should NOT happen)
```
❌ Failed to parse/embed file SomeFile.java: Ollama embedding failed...
   [Full stack trace]
✅ Successfully parsed 149/150 files
❌ Failed files (1): SomeFile.java
```

### During Vector Index Creation
```
✅ Neo4j property indexes created
✅ Created vector index: type_embedding_index
✅ Created vector index: method_embedding_index
```

### During Semantic Search
```
🔍 [SEMANTIC SEARCH] Query: 'properties file', Limit: 5
🔷 Generated query embedding: 1024 dimensions
📊 [GRAPH DB REQUEST RAW] Executing raw Cypher query
📊 [GRAPH DB RESPONSE RAW] Query completed in 150ms, Returned 3 rows
✅ [SEMANTIC SEARCH] Found 3 classes, 0 methods
```

## Common Issues & Solutions

### Issue: "No Type nodes found"
**Cause:** Indexing didn't run or failed silently
**Solution:** Check application logs for exceptions, verify Ollama is running

### Issue: "Type nodes exist but no embeddings"
**Cause:** EmbeddingService not being called, or exceptions swallowed
**Solution:** Check logs for embedding generation, verify OllamaClient.embed() succeeds

### Issue: "Vector search returns empty"
**Cause:** Index not populated, or embeddings are empty lists
**Solution:** Drop and recreate indexes after verifying embeddings exist

### Issue: "Agent uses search_code instead of semantic_search"
**Cause:** LLM too weak for complex routing (qwen2.5-coder:7b)
**Solution:** Test with qwen2.5-coder:32b (already configured in application.yml)
