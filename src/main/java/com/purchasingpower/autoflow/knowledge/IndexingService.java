package com.purchasingpower.autoflow.knowledge;

import com.purchasingpower.autoflow.core.Repository;

import java.util.concurrent.CompletableFuture;

/**
 * Service for indexing repositories into the knowledge graph.
 *
 * @since 2.0.0
 */
public interface IndexingService {

    IndexingResult indexRepository(Repository repository);

    CompletableFuture<IndexingResult> indexRepositoryAsync(Repository repository);

    IndexingResult reindexRepository(String repositoryId);

    IndexingStatus getIndexingStatus(String repositoryId);
}
