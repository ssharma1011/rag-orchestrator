package com.purchasingpower.autoflow.core.impl;

import com.purchasingpower.autoflow.core.CodeEntity;
import com.purchasingpower.autoflow.core.EntityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of CodeEntity.
 *
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeEntityImpl implements CodeEntity {

    private String id;
    private EntityType type;
    private String repositoryId;
    private String name;
    private String fullyQualifiedName;
    private String filePath;
    private int startLine;
    private int endLine;
    private String sourceCode;
    private String summary;

    @Builder.Default
    private List<String> annotations = new ArrayList<>();
}
