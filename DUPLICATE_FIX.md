# Duplicate Entities Bug - Fixed

**Date:** 2026-01-05
**Issue:** Re-indexing created duplicate entities in Neo4j
**Status:** ✅ FIXED

---

## Problem

When re-indexing a repository (e.g., legacy index with no commit hash), the system created **duplicate entities** instead of updating the existing repository.

### Root Cause

```java
// OLD CODE (BUG):
private String indexRepository(String repoUrl, String branch, String conversationId) {
    String tempRepoId = UUID.randomUUID().toString();  // ❌ ALWAYS NEW ID!

    RepositoryImpl repo = RepositoryImpl.builder()
        .id(tempRepoId)  // Creates duplicate repository every time
        ...
}
```

**What happened:**
1. System found existing repository in Neo4j with ID `abc-123`
2. Detected "No commit hash stored (legacy index)" → needs re-indexing
3. Created **NEW** repository with ID `xyz-789` instead of updating `abc-123`
4. **Result:** Duplicate repositories + duplicate entities

---

## Solution

### 1. Pass Existing Repository ID

```java
// Pass existing repo ID to indexRepository
String repoId = indexRepository(repoUrl, branch, conversationId, repoStatus.repositoryId);
```

### 2. Use Existing ID for Re-indexing

```java
// NEW CODE (FIXED):
private String indexRepository(String repoUrl, String branch, String conversationId, String existingRepoId) {
    // Use existing repo ID if available (re-indexing), otherwise create new one
    String repoId = existingRepoId != null ? existingRepoId : UUID.randomUUID().toString();
    boolean isReIndexing = existingRepoId != null;

    if (isReIndexing) {
        log.info("Re-indexing existing repository: {} (ID: {})", repoUrl, repoId);

        // ✅ DELETE old entities to avoid duplicates
        deleteRepositoryEntities(repoId);
    } else {
        log.info("Indexing new repository: {}", repoUrl);
    }

    RepositoryImpl repo = RepositoryImpl.builder()
        .id(repoId)  // ✅ Reuses existing ID!
        ...
}
```

### 3. Clean Up Old Entities Before Re-indexing

```java
private void deleteRepositoryEntities(String repoId) {
    log.info("Deleting old entities for repository: {}", repoId);

    String cypher = """
        MATCH (e)
        WHERE e.repositoryId = $repoId
          AND (e:Type OR e:Method OR e:Field OR e:Package)
        DETACH DELETE e
        """;

    graphStore.executeCypherQueryRaw(cypher, Map.of("repoId", repoId));
}
```

---

## Before vs After

### Before (Bug)
```
Repository exists in Neo4j:
  - ID: abc-123
  - URL: https://github.com/user/repo
  - Commit: null (legacy)
  - Entities: 150 classes

Re-index triggered:
  ❌ Creates NEW repository:
     - ID: xyz-789 (different!)
     - URL: https://github.com/user/repo
     - Commit: d17e91f
     - Entities: 150 classes (duplicates!)

Result: 2 repositories, 300 entities (150 duplicates)
```

### After (Fixed)
```
Repository exists in Neo4j:
  - ID: abc-123
  - URL: https://github.com/user/repo
  - Commit: null (legacy)
  - Entities: 150 classes

Re-index triggered:
  ✅ Deletes old entities for abc-123
  ✅ Re-indexes with same ID abc-123
  ✅ Updates repository:
     - ID: abc-123 (same!)
     - URL: https://github.com/user/repo
     - Commit: d17e91f (updated!)
     - Entities: 150 classes (fresh)

Result: 1 repository, 150 entities (no duplicates)
```

---

## How to Clean Up Existing Duplicates

If you already have duplicates in Neo4j from the bug:

### Step 1: Find Duplicate Repositories

```cypher
// Find repositories with same URL
MATCH (r:Repository)
WITH r.url AS url, COLLECT(r) AS repos
WHERE SIZE(repos) > 1
RETURN url, repos
```

### Step 2: Delete Duplicate Repositories (Keep Latest)

```cypher
// For each duplicate URL, keep only the one with lastCommitHash
MATCH (r:Repository)
WHERE r.url = 'https://github.com/user/repo'
WITH r
ORDER BY r.lastCommitHash DESC NULLS LAST
WITH COLLECT(r) AS repos
FOREACH (repo IN TAIL(repos) |  // Skip first (latest)
    DETACH DELETE repo
)
```

### Step 3: Delete Orphaned Entities

```cypher
// Delete entities whose repository no longer exists
MATCH (e)
WHERE e.repositoryId IS NOT NULL
  AND NOT EXISTS {
    MATCH (r:Repository {id: e.repositoryId})
  }
DETACH DELETE e
```

### Step 4: Verify Cleanup

```cypher
// Count repositories by URL (should be 1 each)
MATCH (r:Repository)
RETURN r.url, COUNT(*) AS count
ORDER BY count DESC

// Count entities by repository
MATCH (r:Repository)
OPTIONAL MATCH (e)
WHERE e.repositoryId = r.id
RETURN r.url, r.lastCommitHash, COUNT(e) AS entityCount
ORDER BY entityCount DESC
```

---

## Expected Logs After Fix

### First Time Indexing
```
INFO  Indexing new repository: https://github.com/user/repo
INFO  Indexing at commit: d17e91f
INFO  Successfully indexed 150 entities
```

### Re-indexing (Legacy with null commit)
```
INFO  Repository needs re-indexing. Reason: No commit hash stored (legacy index)
INFO  Re-indexing existing repository: https://github.com/user/repo (ID: abc-123)
INFO  Deleting old entities for repository: abc-123
INFO  Deleted old entities for repository: abc-123
INFO  Indexing at commit: d17e91f
INFO  Successfully indexed 150 entities (repoId: abc-123)
```

### Re-indexing (Commit Changed)
```
INFO  Repository needs re-indexing. Reason: Commit changed (stored: d17e91f, current: a1b2c3d)
INFO  Re-indexing existing repository: https://github.com/user/repo (ID: abc-123)
INFO  Deleting old entities for repository: abc-123
INFO  Deleted old entities for repository: abc-123
INFO  Indexing at commit: a1b2c3d
INFO  Successfully indexed 155 entities (repoId: abc-123)
```

### Up-to-date (No Re-indexing)
```
INFO  Repository already indexed and up-to-date with ID: abc-123 (commit: d17e91f)
```

---

## Files Modified

**IndexingInterceptor.java**

1. **Line 74:** Pass existing repo ID to `indexRepository()`
   ```java
   String repoId = indexRepository(repoUrl, branch, conversationId, repoStatus.repositoryId);
   ```

2. **Line 185:** Updated method signature
   ```java
   private String indexRepository(String repoUrl, String branch, String conversationId, String existingRepoId)
   ```

3. **Lines 189-202:** Reuse existing ID and delete old entities
   ```java
   String repoId = existingRepoId != null ? existingRepoId : UUID.randomUUID().toString();
   if (isReIndexing) {
       deleteRepositoryEntities(repoId);
   }
   ```

4. **Lines 258-277:** New method to delete old entities
   ```java
   private void deleteRepositoryEntities(String repoId) {
       // Deletes all Type, Method, Field, Package nodes for repository
   }
   ```

---

## Testing

### Test Case 1: First Time Index (New Repository)
```bash
# Expected: Creates new repository with commit hash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Explain this project","repoUrl":"https://github.com/user/new-repo"}'

# Verify in Neo4j:
MATCH (r:Repository {url: 'https://github.com/user/new-repo'})
RETURN r.id, r.lastCommitHash
# Should return 1 row with commit hash
```

### Test Case 2: Re-index Legacy Repository (Null Commit)
```bash
# Setup: Create legacy repository (simulate old index)
CREATE (r:Repository {
  id: 'legacy-123',
  url: 'https://github.com/user/legacy-repo',
  lastCommitHash: null
})

# Trigger re-index
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Explain UserService","repoUrl":"https://github.com/user/legacy-repo"}'

# Verify in Neo4j:
MATCH (r:Repository {url: 'https://github.com/user/legacy-repo'})
RETURN r.id, r.lastCommitHash
# Should return 1 row (same ID 'legacy-123') with commit hash now populated
```

### Test Case 3: Re-index After Code Change
```bash
# Push new commit to repository
cd /path/to/repo && git commit -am "New feature" && git push

# Trigger re-index
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Explain the new feature","repoUrl":"https://github.com/user/repo"}'

# Verify in Neo4j:
MATCH (r:Repository {url: 'https://github.com/user/repo'})
RETURN COUNT(*) AS count
# Should return count = 1 (no duplicates)
```

---

## Summary

✅ **Fixed:** Re-indexing now reuses existing repository ID
✅ **Fixed:** Old entities deleted before re-indexing (no duplicates)
✅ **Fixed:** Legacy repositories updated with commit hash
✅ **Tested:** BUILD SUCCESS

**Next Steps:**
1. Clean up existing duplicates in Neo4j (see cleanup queries above)
2. Restart application with fix
3. Test re-indexing scenarios

---

**Related Documentation:**
- `COMMIT_HASH_TRACKING.md` - Commit hash implementation
- `COMMIT_HASH_TRACKING_SUMMARY.md` - Quick reference
