package com.purchasingpower.autoflow.service;

import java.io.File;

/**
 * Service for ingesting code into Pinecone vector database.
 *
 * Used by:
 * - CodeIndexerAgent (full repository indexing)
 * - IncrementalEmbeddingSyncService (handles incremental updates)
 */
public interface PineconeIngestService {

    /**
     * Ingest entire repository (full indexing)
     *
     * IMPORTANT: IncrementalEmbeddingSyncService handles incremental updates.
     * This method is for FULL indexing only.
     *
     * @param workspaceDir Repository workspace directory
     * @param repoName Repository name (used as namespace)
     * @return true if code was found and indexed, false if empty/scaffold project
     */
    boolean ingestRepository(File workspaceDir, String repoName);
}