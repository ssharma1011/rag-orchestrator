# Vector Search Testing Plan

## What Was Fixed

1. ✅ **Improved Error Logging**: JavaParserServiceImpl now logs full stack traces and lists all failed files
2. ✅ **Ollama Timeouts**: Already configured (60min response, 10min read/write)
3. ✅ **Diagnostic Script**: Created `diagnose_neo4j.cypher` with 10 comprehensive tests
4. ✅ **Documentation**: Created `VECTOR_SEARCH_FIXES.md` with troubleshooting guide

## Prerequisites

Before starting, verify these services are running:

```bash
# 1. Check Neo4j (should return connection info)
curl http://localhost:7474

# 2. Check Ollama (should list models)
ollama list

# 3. Verify embedding model is installed
ollama list | grep mxbai-embed-large
```

If any service is down:
```bash
# Start Neo4j (Docker)
docker run --rm -p 7687:7687 -p 7474:7474 -e NEO4J_AUTH=neo4j/password neo4j:5.15

# Start Ollama
ollama serve

# Pull embedding model if needed
ollama pull mxbai-embed-large
```

---

## Step 1: Clear Neo4j Database

**IMPORTANT:** Drop indexes too, not just data!

Open Neo4j Browser: http://localhost:7474

Run these queries ONE BY ONE:

```cypher
// 1. Delete all data
MATCH (n) DETACH DELETE n;

// 2. Drop vector indexes
DROP INDEX type_embedding_index IF EXISTS;
DROP INDEX method_embedding_index IF EXISTS;

// 3. Verify empty
MATCH (n) RETURN count(n) as total;
// Should return: total = 0

// 4. Show remaining indexes (should only show property indexes)
SHOW INDEXES;
```

---

## Step 2: Start Application with Verbose Logging

```bash
cd C:\Users\ssharma\personal\rag-orchestrator

# Start application (watch logs carefully)
mvn spring-boot:run
```

**Watch for these startup logs:**
```
✅ Neo4j property indexes created
✅ Created vector index: type_embedding_index
✅ Created vector index: method_embedding_index
```

If you see "⚠️  Failed to create vector indexes", check Neo4j version (needs 5.x+).

---

## Step 3: Trigger Indexing via API

In a **NEW terminal**, run:

```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\": \"explain this project\", \"repoUrl\": \"C:\\\\Users\\\\ssharma\\\\personal\\\\rag-orchestrator\"}"
```

**Expected behavior:**
1. Agent calls `discover_project` tool
2. DiscoverProjectTool triggers auto-indexing (first time only)
3. Indexing begins

---

## Step 4: Monitor Indexing Logs

Watch application logs for this sequence:

### Phase 1: Indexing Start
```
📂 Parsing 150 Java files
Starting indexing for: C:\Users\ssharma\personal\rag-orchestrator
```

### Phase 2: Embedding Generation (CRITICAL - should see hundreds of these)
```
📄 Parsing Java file: ChatController.java
🔵 [EMBEDDING REQUEST] Provider=Ollama, Model=mxbai-embed-large, TextLength=450
🟢 [EMBEDDING RESPONSE] Provider=Ollama, Dimensions=1024
🔷 Generated embedding for class: ChatController (1024 dimensions)
🔷 Generated embedding for method: chat (1024 dimensions)
🔷 Generated embedding for method: streamChat (1024 dimensions)
...
📦 Storing JavaClass: com.purchasingpower.autoflow.api.ChatController
✅ Stored class with 5 methods, 3 fields
```

### Phase 3: Success Check
```
✅ Successfully parsed 150/150 files
Indexing completed: 2064 entities, 1234 relationships in 287937ms
```

### Phase 4: RED FLAGS (Should NOT see these)
```
❌ Failed to parse/embed file SomeFile.java: ...
   [Stack trace]
❌ Failed files (5): File1.java, File2.java, ...
```

**If you see failures:**
- Note which files failed
- Check if ChatController is in the failed list
- Look at the exception message (Ollama down? Memory issue?)

---

## Step 5: Run Diagnostic Queries

Open `diagnose_neo4j.cypher` and run each query in Neo4j Browser.

### Critical Tests:

**Test 1: Do Type nodes exist?**
```cypher
MATCH (t:Type) RETURN count(t) as totalTypes;
```
Expected: `totalTypes > 100` (should have ~150 classes)

**Test 2: Do embeddings exist?**
```cypher
MATCH (t:Type) WHERE t.embedding IS NOT NULL
RETURN count(t) as typesWithEmbeddings;
```
Expected: `typesWithEmbeddings == totalTypes` (100% coverage)

**Test 3: Are embeddings correct size?**
```cypher
MATCH (t:Type) WHERE t.embedding IS NOT NULL
RETURN t.name, size(t.embedding) as dims
LIMIT 5;
```
Expected: All rows show `dims = 1024`

**Test 5: Does ChatController exist with embedding?**
```cypher
MATCH (t:Type {name: 'ChatController'})
RETURN t.name, size(t.embedding) as dims,
       substring(t.description, 0, 200) as desc;
```
Expected: 1 row with dims=1024, desc shows enriched description

**Test 6: Are vector indexes ONLINE?**
```cypher
SHOW INDEXES WHERE type = 'VECTOR';
```
Expected: 2 indexes (type_embedding_index, method_embedding_index), both state='ONLINE', populationPercent=100

---

## Step 6: Test Vector Search Directly

Run this Cypher query to test the vector index:

```cypher
// Find ChatController and use its embedding to search
MATCH (t:Type {name: 'ChatController'})
WHERE t.embedding IS NOT NULL AND size(t.embedding) = 1024
WITH t.embedding as queryEmbedding
CALL db.index.vector.queryNodes('type_embedding_index', 5, queryEmbedding)
YIELD node, score
RETURN node.name, node.packageName, score
ORDER BY score DESC;
```

**Expected Results:**
```
node.name          | node.packageName                    | score
--------------------|-------------------------------------|-------
ChatController     | com.purchasingpower.autoflow.api    | 1.0
KnowledgeController| com.purchasingpower.autoflow.api    | 0.85
SearchController   | com.purchasingpower.autoflow.api    | 0.82
...
```

**If this fails (returns 0 rows):**
- Vector index not working
- Embeddings are empty lists
- Neo4j version issue

---

## Step 7: Test Semantic Search via Tool

Now test if the agent can use semantic search:

```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\": \"find the properties file\", \"repoUrl\": \"C:\\\\Users\\\\ssharma\\\\personal\\\\rag-orchestrator\"}"
```

**Watch logs for:**
```
🔍 [SEMANTIC SEARCH] Query: 'properties file', Limit: 5
🔷 Generated query embedding: 1024 dimensions
📊 [GRAPH DB REQUEST RAW] Executing raw Cypher query
📊 [GRAPH DB RESPONSE RAW] Query completed in 150ms, Returned 3 rows
✅ [SEMANTIC SEARCH] Found 3 classes, 0 methods
```

**Expected Response:**
Should find `application.yml`, `application-dev.yml` or related config classes.

**If agent uses search_code instead of semantic_search:**
- LLM (qwen2.5-coder:7b) may be too weak
- Try with qwen2.5-coder:32b (already configured)

---

## Step 8: Test "Explain Project" Question

The original question that should now work perfectly:

```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\": \"explain this project to me\", \"repoUrl\": \"C:\\\\Users\\\\ssharma\\\\personal\\\\rag-orchestrator\"}"
```

**Expected:**
1. Agent calls `discover_project` (finds all @RestController, @Service, etc.)
2. Agent may call `semantic_search` for specific features
3. Returns comprehensive project explanation

**Quality check:**
- Should mention ChatController, KnowledgeController, SearchController
- Should describe AutoFlowAgent (unified tool-based agent)
- Should mention Neo4j graph storage
- Should identify it as a RAG orchestrator

---

## Troubleshooting Decision Tree

### No Type nodes found (Test 1 fails)
→ Check logs for "Starting indexing"
→ If missing: IndexingManager not triggered
→ If present: Check for exceptions during parsing

### Type nodes exist but no embeddings (Test 2 < Test 1)
→ Check logs for "Generated embedding for"
→ If missing: Ollama not running or wrong model
→ Run: `ollama list | grep mxbai-embed-large`

### Embeddings wrong size (Test 3 shows != 1024)
→ Wrong model being used
→ Check application.yml: `embedding-model: mxbai-embed-large`

### ChatController missing (Test 5 fails)
→ Check logs for "Failed to parse/embed file ChatController.java"
→ If found: Look at exception, fix the issue

### Vector indexes not ONLINE (Test 6)
→ Neo4j version < 5.0 (vector search not supported)
→ Run: `CALL dbms.components()`
→ Upgrade to Neo4j 5.15+

### Vector search returns 0 (Step 6 fails)
→ Index not populated despite showing ONLINE
→ Try: Drop indexes, restart app, reindex
→ Check for empty embeddings: `MATCH (t:Type) WHERE size(t.embedding) = 0 RETURN count(t)`

### Agent uses wrong tool (Step 7)
→ qwen2.5-coder:7b too weak for tool routing
→ In application.yml: Change `chat-model: qwen2.5-coder:7b` → `qwen2.5-coder:32b`
→ Download: `ollama pull qwen2.5-coder:32b`
→ Restart application

---

## Success Criteria

✅ All 150 Java files parsed without failures
✅ All Type nodes have 1024-dim embeddings
✅ Vector indexes are ONLINE with 100% population
✅ Manual vector search returns similar classes
✅ Semantic search tool finds application.yml for "properties file"
✅ "Explain project" returns comprehensive, accurate description

---

## Next Steps After Success

1. Test other conceptual queries:
   - "how does authentication work"
   - "find error handling code"
   - "where is the chat streaming implemented"

2. Test drill-down questions:
   - "explain the AutoFlowAgent"
   - "how does the indexing pipeline work"
   - "what tools are available"

3. Monitor performance:
   - Semantic search should be <200ms
   - Full question+answer should be 1-2s

4. Consider optimizations:
   - Batch embedding generation (reduce Ollama calls)
   - Cache embeddings by file hash
   - Incremental indexing (only changed files)
