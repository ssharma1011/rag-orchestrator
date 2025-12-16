package com.purchasingpower.autoflow.model.sync;

/**
 * Type of embedding sync operation that was performed.
 */
public enum SyncType {

    /**
     * First time indexing this repository.
     * All files were indexed from scratch.
     */
    INITIAL_FULL_INDEX,

    /**
     * User explicitly requested a full reindex.
     * All existing vectors were deleted and recreated.
     */
    FORCED_FULL_REINDEX,

    /**
     * Normal incremental sync.
     * Only changed files since last index were processed.
     */
    INCREMENTAL,

    /**
     * No changes detected since last indexed commit.
     * Nothing was done.
     */
    NO_CHANGES,

    /**
     * An error occurred during sync.
     * Check logs for details.
     */
    ERROR
}