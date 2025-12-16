package com.purchasingpower.autoflow.model.sync;

/**
 * Represents a file that changed between two commits.
 * Used internally by IncrementalEmbeddingSyncService.
 */
public record ChangedFile(
        String path,
        ChangeType changeType
) {
    /**
     * Type of change detected by Git diff.
     */
    public enum ChangeType {
        /** New file added */
        ADD,

        /** Existing file modified */
        MODIFY,

        /** File deleted */
        DELETE
    }
}