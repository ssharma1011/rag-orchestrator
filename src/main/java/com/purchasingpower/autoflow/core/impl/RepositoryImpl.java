package com.purchasingpower.autoflow.core.impl;

import com.purchasingpower.autoflow.core.Repository;
import com.purchasingpower.autoflow.core.RepositoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of Repository.
 *
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryImpl implements Repository {

    private String id;
    private String url;
    private String branch;
    private RepositoryType type;
    private String language;
    private String domain;
    private LocalDateTime lastIndexedAt;
    private String lastIndexedCommit;

    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
}
