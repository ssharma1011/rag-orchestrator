package com.purchasingpower.autoflow.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Index repository response.
 *
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexResponse {

    private boolean success;
    private String repoId;
    private int entitiesCreated;
    private int relationshipsCreated;
    private long durationMs;
    private String error;

    public static IndexResponse success(String repoId, int entities, int relationships, long durationMs) {
        return IndexResponse.builder()
            .success(true)
            .repoId(repoId)
            .entitiesCreated(entities)
            .relationshipsCreated(relationships)
            .durationMs(durationMs)
            .build();
    }

    public static IndexResponse error(String error) {
        return IndexResponse.builder()
            .success(false)
            .error(error)
            .build();
    }
}
