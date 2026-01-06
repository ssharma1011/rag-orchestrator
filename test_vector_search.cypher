// Test 1: Check if embeddings exist
MATCH (t:Type)
WHERE t.embedding IS NOT NULL
RETURN t.name, size(t.embedding) as dims, substring(t.description, 0, 100) as desc
LIMIT 5;

// Test 2: Show vector indexes
SHOW INDEXES
WHERE type = 'VECTOR';

// Test 3: Try vector search (if embeddings exist)
MATCH (t:Type {name: 'ChatController'})
WHERE t.embedding IS NOT NULL
WITH t.embedding as queryEmbedding
CALL db.index.vector.queryNodes('type_embedding_index', 5, queryEmbedding)
YIELD node, score
RETURN node.name, score
ORDER BY score DESC;
