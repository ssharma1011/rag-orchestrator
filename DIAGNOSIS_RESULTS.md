# Neo4j Graph Store Diagnosis

## Problem Summary

From the application logs, we can see:

1. **Search queries return 0 results** when searching for "ChatController"
2. **Annotation-based queries work** - DiscoverProjectTool finds 3 RestControllers
3. **Vector search works** but returns 10 results (quality unclear)

## Most Likely Root Cause

**Type nodes exist but lack the properties needed for text-based searches.**

The query uses `CONTAINS` on these properties:
- `e.name`
- `e.fqn`
- `e.sourceCode`
- `e.filePath`

If any of these are NULL or empty strings, the `CONTAINS` check will fail.

## Diagnostic Steps

### Step 1: Check if Type nodes have required properties

Run this in Neo4j Browser:

```cypher
// Check ChatController specifically
MATCH (t:Type)
WHERE t.name = 'ChatController' OR t.fqn CONTAINS 'ChatController'
RETURN t.id, t.name, t.fqn, t.filePath,
       length(t.sourceCode) as sourceCodeLength,
       t.repositoryId
LIMIT 5;
```

**Expected:** Should return the ChatController node with all properties populated.

### Step 2: Check if properties are NULL

```cypher
// Count nodes with missing properties
MATCH (t:Type)
WHERE t.repositoryId = 'd4aa6f8a-f69a-48d6-aed1-063091eb9da4'
RETURN
  count(t) as totalTypes,
  count(t.name) as withName,
  count(t.fqn) as withFqn,
  count(t.sourceCode) as withSourceCode,
  count(t.filePath) as withFilePath;
```

**Expected:** All counts should be equal. If not, properties are missing.

### Step 3: Test the exact query used by SearchService

```cypher
// This is the exact query from your logs
MATCH (e:Type)
WHERE (toLower(e.name) CONTAINS toLower('chatcontroller')
    OR toLower(e.fqn) CONTAINS toLower('chatcontroller')
    OR toLower(e.sourceCode) CONTAINS toLower('chatcontroller')
    OR toLower(e.filePath) CONTAINS toLower('chatcontroller'))
AND e.repositoryId IN ['d4aa6f8a-f69a-48d6-aed1-063091eb9da4']
RETURN e.name, e.fqn, e.filePath
LIMIT 10;
```

**Expected:** Should return ChatController and other matches.
**If it returns 0:** Properties are NULL or the node doesn't exist.

### Step 4: Check annotation-based query (this works)

```cypher
// This query WORKS according to logs
MATCH (t:Type)-[:ANNOTATED_BY]->(a:Annotation)
WHERE a.fqn CONTAINS 'RestController'
AND t.repositoryId IN ['d4aa6f8a-f69a-48d6-aed1-063091eb9da4']
RETURN t.name, t.fqn
ORDER BY t.fqn
LIMIT 50;
```

**Expected:** Should return 3 RestControllers including ChatController.

### Step 5: Inspect a sample Type node

```cypher
MATCH (t:Type {name: 'ChatController'})
RETURN properties(t) as allProperties;
```

This will show ALL properties on the ChatController node.

## Possible Solutions

### Solution 1: Properties are empty strings instead of NULL

If properties exist but are empty strings `""`, the `CONTAINS` will fail silently.

**Fix:** Update the indexing code to ensure proper values:

```java
// In storeTypeNode method
SET t.name = COALESCE($name, ''),
    t.fqn = COALESCE($fqn, ''),
    t.filePath = COALESCE($filePath, ''),
    t.sourceCode = COALESCE($sourceCode, '')
```

Change to:
```java
SET t.name = $name,  // Will be NULL if not provided
    t.fqn = $fqn,
    t.filePath = $filePath,
    t.sourceCode = $sourceCode
```

### Solution 2: Query needs NULL handling

Update the search query to handle NULL values:

```java
WHERE (
  (e.name IS NOT NULL AND toLower(e.name) CONTAINS toLower($kw0)) OR
  (e.fqn IS NOT NULL AND toLower(e.fqn) CONTAINS toLower($kw0)) OR
  (e.sourceCode IS NOT NULL AND toLower(e.sourceCode) CONTAINS toLower($kw0)) OR
  (e.filePath IS NOT NULL AND toLower(e.filePath) CONTAINS toLower($kw0))
)
```

### Solution 3: Re-index the repository

If the data is corrupt, trigger a full re-index:

1. Delete existing data:
   ```cypher
   MATCH (t:Type {repositoryId: 'd4aa6f8a-f69a-48d6-aed1-063091eb9da4'})
   DETACH DELETE t;
   ```

2. Trigger re-indexing via the API or manually

## Next Steps

1. **Run diagnostic queries** in Neo4j Browser
2. **Share results** with the development team
3. **Apply the appropriate fix** based on findings
4. **Verify** that searches now work

## Vector Search Quality

Separate issue: Even when vector search works, results may be poor quality because:
1. **Description quality** - Embeddings are based on AI-generated descriptions
2. **Similarity threshold** - No minimum score filtering
3. **Context window** - Limited context for description generation

**Recommendation:** Add a minimum similarity score filter (e.g., 0.7) to exclude poor matches.
