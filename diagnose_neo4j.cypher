// ========================================
// NEO4J DIAGNOSTIC SCRIPT
// Run these queries to understand the current state
// ========================================

// TEST 1: Check if ANY Type nodes exist
// Expected: Should return a positive count
MATCH (t:Type)
RETURN count(t) as totalTypes;

// TEST 2: Check if ANY Type nodes have embeddings
// Expected: Should match totalTypes from Test 1
MATCH (t:Type)
WHERE t.embedding IS NOT NULL
RETURN count(t) as typesWithEmbeddings;

// TEST 3: Check embedding dimensions
// Expected: Should show 1024 dimensions
MATCH (t:Type)
WHERE t.embedding IS NOT NULL
RETURN t.name, size(t.embedding) as dimensions
LIMIT 5;

// TEST 4: List all Type nodes
// Expected: Should include ChatController, AutoFlowAgent, etc.
MATCH (t:Type)
RETURN t.name, t.packageName, t.repositoryId
ORDER BY t.name
LIMIT 20;

// TEST 5: Check if ChatController specifically exists
// Expected: Should return 1 row
MATCH (t:Type)
WHERE t.name = 'ChatController'
RETURN t.name, t.fqn, size(t.embedding) as dims,
       substring(t.description, 0, 200) as desc;

// TEST 6: Check vector indexes
// Expected: Should show type_embedding_index and method_embedding_index
SHOW INDEXES
WHERE type = 'VECTOR';

// TEST 7: Check if vector index has data
// Expected: Should match typesWithEmbeddings from Test 2
SHOW INDEXES
YIELD name, type, populationPercent, state
WHERE type = 'VECTOR' AND name = 'type_embedding_index'
RETURN name, state, populationPercent;

// TEST 8: Check Repository nodes
// Expected: Should show your repository with lastIndexedCommit
MATCH (r:Repository)
RETURN r.id, r.url, r.branch, r.lastIndexedAt, r.lastIndexedCommit;

// TEST 9: Sample manual vector search (if embeddings exist)
// This uses a known vector to test the index
MATCH (t:Type {name: 'AutoFlowAgent'})
WHERE t.embedding IS NOT NULL
WITH t.embedding as queryEmbedding
CALL db.index.vector.queryNodes('type_embedding_index', 5, queryEmbedding)
YIELD node, score
RETURN node.name, score
ORDER BY score DESC;

// TEST 10: Check if OLD Entity nodes still exist
// Expected: Should return 0 (we only use Type nodes now)
MATCH (e:Entity)
RETURN count(e) as oldEntityNodes;

// ========================================
// INTERPRETATION GUIDE:
// ========================================
// If Test 1 returns 0: No indexing happened
// If Test 2 < Test 1: Embeddings missing for some nodes
// If Test 3 shows != 1024: Wrong embedding model used
// If Test 5 returns 0: ChatController wasn't indexed (parsing failed)
// If Test 6 returns 0: Vector indexes don't exist
// If Test 7 shows state != 'ONLINE': Index not ready
// If Test 9 returns 0: Vector search is broken
