package com.purchasingpower.autoflow.knowledge;

/**
 * Indexing states.
 *
 * @since 2.0.0
 */
public enum IndexingState {
    NOT_STARTED,
    CLONING,
    PARSING,
    EXTRACTING_RELATIONSHIPS,
    STORING,
    GENERATING_EMBEDDINGS,
    COMPLETED,
    FAILED
}
