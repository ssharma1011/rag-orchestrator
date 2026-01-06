# Commit Hash Tracking - Quick Reference

**Status:** ✅ Implemented | **Date:** 2026-01-05

## What Changed

`IndexingInterceptor` now tracks git commit hashes to detect code changes and automatically re-index repositories when code updates.

## Before vs After

### Before (Bug)
```java
// Only checked if repository exists
String repoId = getRepositoryIdByUrl(repoUrl);
if (repoId == null) {
    indexRepository(); // Index only once
}
// ❌ Never re-indexed even if code changed
```

### After (Fixed)
```java
// Check repository status including commit hash
RepositoryStatus status = checkRepositoryStatus(repoUrl, branch);
if (status.needsIndexing) {
    indexRepository(); // Re-index if:
    // 1. Repository not indexed
    // 2. Commit hash changed
    // 3. No commit hash stored (legacy)
}
```

## Key Implementation Details

### 1. Commit Hash Comparison
```java
private RepositoryStatus checkRepositoryStatus(String repoUrl, String branch) {
    // Get stored commit from Neo4j
    String storedCommitHash = graphStore.query("MATCH (r:Repository) WHERE r.url = $url RETURN r.lastCommitHash");

    // Get current commit from workspace (clone/pull if needed)
    String currentCommitHash = getCurrentCommitHashFromWorkspace(repoUrl, branch);

    // Compare
    if (storedCommitHash == null || !storedCommitHash.equals(currentCommitHash)) {
        return needsReIndexing();
    }
    return upToDate();
}
```

### 2. Automatic Workspace Updates
- First time: Clones repository
- Subsequent: Pulls latest changes
- Then: Gets current commit hash

### 3. Store Commit Hash
```java
RepositoryImpl repo = RepositoryImpl.builder()
    .id(tempRepoId)
    .url(normalizedUrl)
    .branch(branch)
    .lastIndexedCommit(currentCommitHash)  // ✅ Stored!
    .build();
```

## Scenarios

| Scenario | Stored Hash | Current Hash | Action |
|----------|-------------|--------------|--------|
| First index | null | abc1234 | Index |
| Code unchanged | abc1234 | abc1234 | Skip |
| Code changed | abc1234 | xyz5678 | Re-index |
| Legacy repo | null | xyz5678 | Re-index |

## Log Messages

### First Indexing
```
INFO  Repository not indexed indexing. Reason: Repository not indexed. Auto-indexing before search_code
INFO  Indexing at commit: abc1234
INFO  Successfully indexed 150 entities
```

### Code Changed (Re-index)
```
INFO  Repository needs re-indexing. Reason: Commit changed (stored: abc1234, current: xyz5678)
INFO  Indexing at commit: xyz5678
INFO  Successfully indexed 155 entities (5 new)
```

### Up-to-date (Skip)
```
INFO  Repository already indexed and up-to-date with ID: repo-uuid-123 (commit: abc1234)
```

## Files Modified

- `IndexingInterceptor.java` (~100 lines refactored)
  - Added `GitOperationsService` injection
  - Added `checkRepositoryStatus()` method
  - Added `getCurrentCommitHashFromWorkspace()` method
  - Updated `indexRepository()` to store commit hash

## Infrastructure Already Existed

✅ `Repository.getLastIndexedCommit()` interface method
✅ `RepositoryImpl.lastIndexedCommit` field
✅ `Neo4jGraphStoreImpl` storage of `r.lastIndexedCommit`
✅ `GitOperationsService.getCurrentCommitHash()` method

**Just needed to wire it together!**

## Benefits

✅ Automatic change detection - no manual re-indexing
✅ Always current - index matches repository state
✅ Efficient - skip re-indexing when unchanged
✅ Transparent - clear logging of why indexing happens
✅ Backward compatible - handles legacy indexes

## Testing

```bash
# 1. First index
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Explain this project","repoUrl":"https://github.com/user/repo"}'
# Check logs: "Indexing at commit: abc1234"

# 2. Verify skip (no changes)
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Explain UserService","repoUrl":"https://github.com/user/repo"}'
# Check logs: "Repository already indexed and up-to-date"

# 3. Push new commit to repository
git commit -am "Add new feature" && git push

# 4. Verify re-index
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Explain the new feature","repoUrl":"https://github.com/user/repo"}'
# Check logs: "Commit changed (stored: abc1234, current: xyz5678)"
```

## Neo4j Verification

```cypher
MATCH (r:Repository)
RETURN r.url, r.lastCommitHash, r.lastIndexedAt
ORDER BY r.lastIndexedAt DESC;
```

## Configuration

```yaml
# application.yml
app:
  workspace-dir: ${WORKSPACE_DIR:/tmp/ai-orchestrator-workspace}
```

---

**Related Documentation:** See `COMMIT_HASH_TRACKING.md` for complete details.
