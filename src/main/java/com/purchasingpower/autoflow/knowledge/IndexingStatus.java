package com.purchasingpower.autoflow.knowledge;

/**
 * Status of an indexing operation.
 *
 * @since 2.0.0
 */
public interface IndexingStatus {
    String getRepositoryId();
    IndexingState getState();
    int getProgress();
    String getCurrentStep();
    long getStartedAt();
}
