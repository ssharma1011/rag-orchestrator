package com.purchasingpower.autoflow.model.sync;

import lombok.Builder;
import lombok.Data;

/**
 * Result of an embedding sync operation.
 *
 * Contains statistics about what was synced:
 * - How many files were analyzed
 * - How many chunks were deleted/created
 * - Timing information
 * - Commit references
 */
@Data
@Builder
public class EmbeddingSyncResult {

    private final SyncType syncType;
    private final int filesAnalyzed;
    private final int filesChanged;
    private final int chunksDeleted;
    private final int chunksCreated;
    private final long embeddingTimeMs;
    private final long totalTimeMs;
    private final String fromCommit;
    private final String toCommit;

    /**
     * Human-readable summary of the sync operation.
     */
    public String summary() {
        return String.format(
                "[%s] %d files changed, %d chunks deleted, %d created in %dms",
                syncType, filesChanged, chunksDeleted, chunksCreated, totalTimeMs
        );
    }

    /**
     * Returns true if any actual work was done.
     */
    public boolean hadChanges() {
        return chunksDeleted > 0 || chunksCreated > 0;
    }

    /**
     * Returns true if the sync completed successfully.
     */
    public boolean isSuccess() {
        return syncType != SyncType.ERROR;
    }
}