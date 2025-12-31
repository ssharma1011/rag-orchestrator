package com.purchasingpower.autoflow.knowledge;

import com.purchasingpower.autoflow.core.Repository;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for indexing repositories into the knowledge graph.
 *
 * <p>Handles the complete indexing pipeline:
 * <ol>
 *   <li>Clone or update repository</li>
 *   <li>Parse source code into entities</li>
 *   <li>Extract relationships</li>
 *   <li>Store in graph database</li>
 *   <li>Generate embeddings (optional)</li>
 * </ol>
 *
 * @since 2.0.0
 */
public interface IndexingService {

    /**
     * Index a repository (full or incremental based on state).
     *
     * @param repository Repository to index
     * @return Indexing result
     */
    IndexingResult indexRepository(Repository repository);

    /**
     * Index a repository asynchronously.
     *
     * @param repository Repository to index
     * @return Future with indexing result
     */
    CompletableFuture<IndexingResult> indexRepositoryAsync(Repository repository);

    /**
     * Re-index a repository from scratch.
     *
     * @param repositoryId Repository ID to reindex
     * @return Indexing result
     */
    IndexingResult reindexRepository(String repositoryId);

    /**
     * Get indexing status for a repository.
     *
     * @param repositoryId Repository ID
     * @return Current indexing status
     */
    IndexingStatus getIndexingStatus(String repositoryId);

    /**
     * Result of an indexing operation.
     */
    interface IndexingResult {
        boolean isSuccess();
        int getEntitiesCreated();
        int getRelationshipsCreated();
        int getEmbeddingsGenerated();
        long getDurationMs();
        List<String> getErrors();
    }

    /**
     * Status of an indexing operation.
     */
    interface IndexingStatus {
        String getRepositoryId();
        IndexingState getState();
        int getProgress();  // 0-100
        String getCurrentStep();
        long getStartedAt();
    }

    /**
     * Indexing states.
     */
    enum IndexingState {
        NOT_STARTED,
        CLONING,
        PARSING,
        EXTRACTING_RELATIONSHIPS,
        STORING,
        GENERATING_EMBEDDINGS,
        COMPLETED,
        FAILED
    }
}
