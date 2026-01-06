package com.purchasingpower.autoflow.knowledge.impl;

import com.purchasingpower.autoflow.knowledge.IndexingState;
import com.purchasingpower.autoflow.knowledge.IndexingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Default implementation of IndexingStatus.
 *
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexingStatusImpl implements IndexingStatus {

    private String repositoryId;

    @Builder.Default
    private IndexingState state = IndexingState.NOT_STARTED;

    private int progress;
    private String currentStep;
    private long startedAt;

    public static IndexingStatusImpl notStarted(String repositoryId) {
        return IndexingStatusImpl.builder()
            .repositoryId(repositoryId)
            .state(IndexingState.NOT_STARTED)
            .progress(0)
            .currentStep("Not started")
            .build();
    }

    public static IndexingStatusImpl inProgress(String repositoryId, IndexingState state, int progress, String step) {
        return IndexingStatusImpl.builder()
            .repositoryId(repositoryId)
            .state(state)
            .progress(progress)
            .currentStep(step)
            .startedAt(System.currentTimeMillis())
            .build();
    }

    public static IndexingStatusImpl completed(String repositoryId) {
        return IndexingStatusImpl.builder()
            .repositoryId(repositoryId)
            .state(IndexingState.COMPLETED)
            .progress(100)
            .currentStep("Completed")
            .build();
    }

    public static IndexingStatusImpl failed(String repositoryId, String reason) {
        return IndexingStatusImpl.builder()
            .repositoryId(repositoryId)
            .state(IndexingState.FAILED)
            .currentStep("Failed: " + reason)
            .build();
    }
}
