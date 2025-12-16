package com.purchasingpower.autoflow.service;

import com.purchasingpower.autoflow.model.ast.CodeChunk;
import java.util.List;

/**
 * Service contract for persisting code structure to a Graph Database.
 * Decouples the storage implementation (Oracle/Neo4j) from the core logic.
 */
public interface GraphPersistenceService {

    /**
     * Persists a list of parsed code chunks into the graph database.
     * This process handles the extraction of Nodes (Classes/Methods) and Edges (Dependencies).
     *
     * @param chunks   The list of AST-parsed code chunks.
     * @param repoName The repository name these chunks belong to.
     */
    void persistChunks(List<CodeChunk> chunks, String repoName);
}