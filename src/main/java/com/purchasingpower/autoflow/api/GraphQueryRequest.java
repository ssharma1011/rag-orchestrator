package com.purchasingpower.autoflow.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Graph query request.
 *
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphQueryRequest {

    private String cypher;
    private Map<String, Object> parameters;
}
