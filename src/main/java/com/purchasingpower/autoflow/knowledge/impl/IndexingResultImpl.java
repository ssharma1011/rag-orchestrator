package com.purchasingpower.autoflow.knowledge.impl;

import com.purchasingpower.autoflow.knowledge.IndexingResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of IndexingResult.
 *
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexingResultImpl implements IndexingResult {

    private boolean success;
    private String repositoryId;
    private int entitiesCreated;
    private int relationshipsCreated;
    private int embeddingsGenerated;
    private long durationMs;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public static IndexingResultImpl success(String repositoryId, int entities, int relationships, long durationMs) {
        return IndexingResultImpl.builder()
            .success(true)
            .repositoryId(repositoryId)
            .entitiesCreated(entities)
            .relationshipsCreated(relationships)
            .durationMs(durationMs)
            .build();
    }

    public static IndexingResultImpl failure(List<String> errors, long durationMs) {
        return IndexingResultImpl.builder()
            .success(false)
            .errors(errors)
            .durationMs(durationMs)
            .build();
    }

    public static IndexingResultImpl failure(String error, long durationMs) {
        return IndexingResultImpl.builder()
            .success(false)
            .errors(List.of(error))
            .durationMs(durationMs)
            .build();
    }
}
