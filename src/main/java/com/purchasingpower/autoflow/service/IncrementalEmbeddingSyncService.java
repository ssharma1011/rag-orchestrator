package com.purchasingpower.autoflow.service;

import com.purchasingpower.autoflow.model.sync.EmbeddingSyncResult;

import java.io.File;

/**
 * Service for intelligently syncing code embeddings to Pinecone.
 *
 * KEY FEATURES:
 * 1. Incremental sync - only re-indexes changed files
 * 2. Stores last indexed commit IN Pinecone (survives restarts)
 * 3. Handles multi-workspace scenarios correctly
 *
 * USAGE IN PIPELINE:
 * <pre>
 * // In RagGenerationStep or similar
 * EmbeddingSyncResult result = embeddingSyncService.syncEmbeddings(workspaceDir, repoName);
 * log.info("Sync complete: {}", result.summary());
 * </pre>
 *
 * HOW IT WORKS:
 * <pre>
 * 1. Fetch last_indexed_commit from Pinecone (stored as metadata vector)
 * 2. Git diff: last_indexed_commit..HEAD → find changed files
 * 3. For each changed file:
 *    - Delete old vectors (all chunks for that file)
 *    - Parse new version with AST
 *    - Embed and upsert new chunks
 * 4. Update last_indexed_commit in Pinecone
 * </pre>
 */
public interface IncrementalEmbeddingSyncService {

    /**
     * Syncs code embeddings to Pinecone, indexing only what changed.
     *
     * This is the main method to call from the pipeline.
     * It automatically determines whether to do a full index or incremental sync.
     *
     * @param workspaceDir The cloned repository directory
     * @param repoName Repository name (used for filtering in Pinecone)
     * @return EmbeddingSyncResult with statistics about what was done
     */
    EmbeddingSyncResult syncEmbeddings(File workspaceDir, String repoName);

    /**
     * Forces a complete re-index. Deletes all existing vectors first.
     *
     * Use sparingly - only for:
     * - First time indexing a repo
     * - Pinecone index was corrupted/deleted
     * - Major refactoring (>50% files changed)
     * - Schema change in how we store vectors
     *
     * @param workspaceDir The cloned repository directory
     * @param repoName Repository name
     * @return EmbeddingSyncResult with statistics
     */
    EmbeddingSyncResult forceFullReindex(File workspaceDir, String repoName);

    /**
     * Gets the last indexed commit hash from Pinecone.
     *
     * Useful for debugging or checking sync status.
     *
     * @param repoName Repository name
     * @return Commit hash, or null if repo was never indexed
     */
    String getLastIndexedCommit(String repoName);
}