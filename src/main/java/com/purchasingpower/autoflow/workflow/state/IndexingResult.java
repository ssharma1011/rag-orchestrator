package com.purchasingpower.autoflow.workflow.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of code indexing (Pinecone + Oracle)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexingResult {

    private boolean success;

    private int filesProcessed;
    private int chunksCreated;
    private int graphNodesCreated;
    private int graphEdgesCreated;

    /**
     * Commit that was indexed
     */
    private String indexedCommit;

    /**
     * Was this full or incremental?
     */
    private IndexType indexType;

    /**
     * Files that failed to parse
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    private long durationMs;

    public enum IndexType {
        FULL,         // Full reindex
        INCREMENTAL,  // Only changed files
        SKIPPED       // Already up-to-date
    }
}