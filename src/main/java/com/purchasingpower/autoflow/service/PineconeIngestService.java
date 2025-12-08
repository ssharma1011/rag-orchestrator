package com.purchasingpower.autoflow.service;


import java.io.File;

public interface PineconeIngestService {

    /**
     * Scans the local workspace for source code.
     * If code exists, it generates embeddings and syncs them to Pinecone.
     *
     * @param workspaceDir The local directory where the repo is cloned.
     * @param repoName The name of the repository (used for metadata filtering).
     * @return true if code was found and ingested; false if the repo is empty (new project).
     */
    boolean ingestRepository(File workspaceDir, String repoName);
}