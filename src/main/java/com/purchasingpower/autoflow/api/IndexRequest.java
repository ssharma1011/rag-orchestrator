package com.purchasingpower.autoflow.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Index repository request.
 *
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexRequest {

    private String repoUrl;
    private String branch;
    private String language;
    private String domain;
}
