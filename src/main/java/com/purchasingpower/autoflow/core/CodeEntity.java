package com.purchasingpower.autoflow.core;

import java.util.List;

/**
 * Base interface for code entities (classes, methods, files).
 *
 * @since 2.0.0
 */
public interface CodeEntity {

    String getId();

    EntityType getType();

    String getRepositoryId();

    String getName();

    String getFullyQualifiedName();

    String getFilePath();

    int getStartLine();

    int getEndLine();

    String getSourceCode();

    String getSummary();

    List<String> getAnnotations();
}
