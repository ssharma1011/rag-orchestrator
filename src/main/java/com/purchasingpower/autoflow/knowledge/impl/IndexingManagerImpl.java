package com.purchasingpower.autoflow.knowledge.impl;

import com.purchasingpower.autoflow.core.Repository;
import com.purchasingpower.autoflow.core.impl.RepositoryImpl;
import com.purchasingpower.autoflow.knowledge.GraphStore;
import com.purchasingpower.autoflow.knowledge.IndexingManager;
import com.purchasingpower.autoflow.knowledge.IndexingResult;
import com.purchasingpower.autoflow.knowledge.IndexingService;
import com.purchasingpower.autoflow.service.GitOperationsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of IndexingManager with automatic index freshness checks.
 *
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingManagerImpl implements IndexingManager {

    private final GraphStore graphStore;
    private final IndexingService indexingService;
    private final GitOperationsService gitOperationsService;

    // Track ongoing indexing operations to prevent duplicates
    private final ConcurrentHashMap<String, CompletableFuture<String>> ongoingIndexing = new ConcurrentHashMap<>();

    @Override
    public String ensureIndexed(String repoUrl, String branch) {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IllegalArgumentException("Repository URL is required");
        }

        String normalizedBranch = branch != null && !branch.isBlank() ? branch : "main";
        String cacheKey = repoUrl + ":" + normalizedBranch;

        log.info("🔍 Ensuring repository is indexed: {} (branch: {})", repoUrl, normalizedBranch);

        // Check current status
        IndexStatus status = checkIndexStatus(repoUrl, normalizedBranch);

        switch (status.state()) {
            case UP_TO_DATE:
                log.info("✅ Repository already indexed and up-to-date: {}", status.repositoryId());
                return status.repositoryId();

            case INDEXING:
                log.info("⏳ Repository is currently being indexed, waiting...");
                return waitForIndexing(cacheKey, repoUrl, normalizedBranch);

            case NOT_INDEXED:
                log.info("📥 Repository not indexed, triggering indexing...");
                return triggerIndexing(cacheKey, repoUrl, normalizedBranch);

            case OUTDATED:
                log.info("🔄 Repository outdated (current: {}, indexed: {}), re-indexing...",
                    status.currentCommit(), status.lastIndexedCommit());
                // Delete old index and re-index
                graphStore.deleteRepository(status.repositoryId());
                return triggerIndexing(cacheKey, repoUrl, normalizedBranch);

            case FAILED:
                log.warn("⚠️  Last indexing failed, retrying...");
                return triggerIndexing(cacheKey, repoUrl, normalizedBranch);

            default:
                throw new IllegalStateException("Unknown index state: " + status.state());
        }
    }

    @Override
    public IndexStatus checkIndexStatus(String repoUrl, String branch) {
        String normalizedBranch = branch != null && !branch.isBlank() ? branch : "main";

        // Find repository in database by URL
        Optional<Repository> existingRepo = findRepositoryByUrl(repoUrl, normalizedBranch);

        if (existingRepo.isEmpty()) {
            return new IndexStatus(IndexState.NOT_INDEXED, null, null, null, 0);
        }

        Repository repo = existingRepo.get();
        String repoId = repo.getId();
        String lastIndexedCommit = repo.getLastIndexedCommit();

        // Check if currently indexing
        if (isIndexing(repoUrl, normalizedBranch)) {
            return new IndexStatus(IndexState.INDEXING, repoId, lastIndexedCommit, null, 0);
        }

        // Get current commit hash from git
        String currentCommit = getCurrentCommitHash(repoUrl, normalizedBranch);

        // Compare commits
        if (currentCommit != null && currentCommit.equals(lastIndexedCommit)) {
            long lastIndexedAt = repo.getLastIndexedAt() != null
                ? repo.getLastIndexedAt().toEpochSecond(java.time.ZoneOffset.UTC)
                : 0;
            return new IndexStatus(IndexState.UP_TO_DATE, repoId, lastIndexedCommit, currentCommit, lastIndexedAt);
        }

        return new IndexStatus(IndexState.OUTDATED, repoId, lastIndexedCommit, currentCommit, 0);
    }

    @Override
    public Optional<Repository> getRepositoryByUrl(String repoUrl, String branch) {
        String normalizedBranch = branch != null && !branch.isBlank() ? branch : "main";
        return findRepositoryByUrl(repoUrl, normalizedBranch);
    }

    /**
     * Find repository in database by URL and branch.
     */
    private Optional<Repository> findRepositoryByUrl(String repoUrl, String branch) {
        // Get all repositories and find matching URL + branch
        List<Repository> allRepos = graphStore.listRepositories();

        return allRepos.stream()
            .filter(r -> normalizeUrl(r.getUrl()).equals(normalizeUrl(repoUrl)))
            .filter(r -> {
                String repoBranch = r.getBranch() != null ? r.getBranch() : "main";
                return repoBranch.equals(branch);
            })
            .findFirst();
    }

    /**
     * Normalize URL for comparison (handle trailing slashes, .git suffix, etc.)
     */
    private String normalizeUrl(String url) {
        if (url == null) return "";

        String normalized = url.trim();

        // Remove trailing slash
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // Remove .git suffix
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }

        // Normalize Windows paths
        normalized = normalized.replace("\\", "/");

        return normalized.toLowerCase();
    }

    /**
     * Get current git commit hash from repository.
     */
    private String getCurrentCommitHash(String repoUrl, String branch) {
        try {
            if (isLocalPath(repoUrl)) {
                // For local paths, get commit from local git repo
                return gitOperationsService.getCurrentCommitHash(new File(repoUrl));
            } else {
                // For remote repos, would need to fetch (expensive)
                // For now, skip this check for remote repos
                log.debug("Skipping commit check for remote repository: {}", repoUrl);
                return null;
            }
        } catch (Exception e) {
            log.warn("Failed to get current commit for {}: {}", repoUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Check if repository is currently being indexed.
     */
    private boolean isIndexing(String repoUrl, String branch) {
        String cacheKey = repoUrl + ":" + branch;
        CompletableFuture<String> ongoing = ongoingIndexing.get(cacheKey);
        return ongoing != null && !ongoing.isDone();
    }

    /**
     * Wait for ongoing indexing to complete.
     */
    private String waitForIndexing(String cacheKey, String repoUrl, String branch) {
        CompletableFuture<String> ongoing = ongoingIndexing.get(cacheKey);
        if (ongoing != null) {
            try {
                return ongoing.get(); // Block until indexing completes
            } catch (Exception e) {
                log.error("Failed to wait for indexing: {}", e.getMessage());
                // If waiting failed, trigger new indexing
                return triggerIndexing(cacheKey, repoUrl, branch);
            }
        }
        // If no ongoing indexing found, trigger new one
        return triggerIndexing(cacheKey, repoUrl, branch);
    }

    /**
     * Trigger asynchronous indexing.
     */
    private String triggerIndexing(String cacheKey, String repoUrl, String branch) {
        // Check if already triggered
        CompletableFuture<String> existing = ongoingIndexing.get(cacheKey);
        if (existing != null && !existing.isDone()) {
            log.debug("Indexing already in progress for {}", cacheKey);
            return waitForIndexing(cacheKey, repoUrl, branch);
        }

        // Start new indexing
        CompletableFuture<String> indexingFuture = CompletableFuture.supplyAsync(() -> {
            try {
                log.info("🚀 Starting indexing for: {} (branch: {})", repoUrl, branch);

                RepositoryImpl repo = RepositoryImpl.builder()
                    .url(repoUrl)
                    .branch(branch)
                    .language("Java")
                    .build();

                IndexingResult result = indexingService.indexRepository(repo);

                if (result.isSuccess()) {
                    log.info("✅ Indexing completed: {} entities, {} relationships",
                        result.getEntitiesCreated(), result.getRelationshipsCreated());
                    return result.getRepositoryId();
                } else {
                    log.error("❌ Indexing failed: {}", String.join(", ", result.getErrors()));
                    throw new RuntimeException("Indexing failed: " + String.join(", ", result.getErrors()));
                }
            } finally {
                // Remove from cache when done
                ongoingIndexing.remove(cacheKey);
            }
        });

        ongoingIndexing.put(cacheKey, indexingFuture);

        // Wait for indexing to complete (blocking)
        try {
            return indexingFuture.get();
        } catch (Exception e) {
            log.error("❌ Indexing failed: {}", e.getMessage());
            throw new RuntimeException("Indexing failed", e);
        }
    }

    /**
     * Check if URL is a local file path.
     */
    private boolean isLocalPath(String url) {
        return !url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("git@");
    }
}
