package com.purchasingpower.autoflow.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Search response.
 *
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

    private boolean success;
    private String error;

    @Builder.Default
    private List<Result> results = new ArrayList<>();

    public static SearchResponse success(List<Result> results) {
        return SearchResponse.builder()
            .success(true)
            .results(results)
            .build();
    }

    public static SearchResponse error(String error) {
        return SearchResponse.builder()
            .success(false)
            .error(error)
            .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private String entityId;
        private String type;
        private String name;
        private String filePath;
        private String snippet;
        private double score;
    }
}
