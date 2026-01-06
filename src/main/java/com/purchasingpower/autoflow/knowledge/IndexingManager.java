package com.purchasingpower.autoflow.knowledge;

import com.purchasingpower.autoflow.core.Repository;

import java.util.Optional;

/**
 * Manages automatic indexing and index freshness checks.
 *
 * Ensures repositories are indexed before queries execute,
 * automatically triggering re-indexing when code changes are detected.
 *
 * @since 2.0.0
 */
public interface IndexingManager {

    /**
     * Ensure a repository is indexed and up-to-date.
     *
     * Checks:
     * 1. Is repo already indexed?
     * 2. If yes, has code changed (git commit hash)?
     * 3. If no/outdated → triggers indexing
     *
     * @param repoUrl Repository URL or local path
     * @param branch Branch name (default: main)
     * @return Repository ID (existing or newly created)
     */
    String ensureIndexed(String repoUrl, String branch);

    /**
     * Check if a repository is indexed and up-to-date.
     *
     * @param repoUrl Repository URL or local path
     * @param branch Branch name
     * @return IndexStatus with state and details
     */
    IndexStatus checkIndexStatus(String repoUrl, String branch);

    /**
     * Get repository by URL and branch.
     *
     * @param repoUrl Repository URL or local path
     * @param branch Branch name
     * @return Repository if indexed, empty otherwise
     */
    Optional<Repository> getRepositoryByUrl(String repoUrl, String branch);

    /**
     * Status of repository indexing.
     */
    enum IndexState {
        NOT_INDEXED,      // Never indexed
        UP_TO_DATE,       // Indexed and current
        OUTDATED,         // Indexed but code changed
        INDEXING,         // Currently being indexed
        FAILED            // Last indexing attempt failed
    }

    /**
     * Index status details.
     */
    record IndexStatus(
        IndexState state,
        String repositoryId,
        String lastIndexedCommit,
        String currentCommit,
        long lastIndexedAt
    ) {}
}
