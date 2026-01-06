package com.purchasingpower.autoflow.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Graph query response.
 *
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphQueryResponse {

    private boolean success;
    private String error;

    @Builder.Default
    private List<Map<String, Object>> results = new ArrayList<>();

    public static GraphQueryResponse success(List<Map<String, Object>> results) {
        return GraphQueryResponse.builder()
            .success(true)
            .results(results)
            .build();
    }

    public static GraphQueryResponse error(String error) {
        return GraphQueryResponse.builder()
            .success(false)
            .error(error)
            .build();
    }
}
