package com.purchasingpower.autoflow.knowledge;

import java.util.List;

/**
 * Result of an indexing operation.
 *
 * @since 2.0.0
 */
public interface IndexingResult {
    boolean isSuccess();
    String getRepositoryId();
    int getEntitiesCreated();
    int getRelationshipsCreated();
    int getEmbeddingsGenerated();
    long getDurationMs();
    List<String> getErrors();
}
