package com.purchasingpower.autoflow.core;

import java.util.List;

/**
 * Base interface for code entities (classes, methods, files).
 *
 * <p>All code entities in the knowledge graph implement this interface,
 * enabling uniform handling in search results and graph operations.
 *
 * @since 2.0.0
 */
public interface CodeEntity {

    /**
     * Unique identifier for this entity.
     * Format: "{repoId}:{fullyQualifiedName}"
     */
    String getId();

    /**
     * Type of this entity.
     */
    EntityType getType();

    /**
     * Repository this entity belongs to.
     */
    String getRepositoryId();

    /**
     * Simple name (e.g., "PaymentService" or "processPayment").
     */
    String getName();

    /**
     * Fully qualified name (e.g., "com.example.service.PaymentService").
     */
    String getFullyQualifiedName();

    /**
     * Source file path relative to repository root.
     */
    String getFilePath();

    /**
     * Line number where this entity starts.
     */
    int getStartLine();

    /**
     * Line number where this entity ends.
     */
    int getEndLine();

    /**
     * Source code content.
     */
    String getSourceCode();

    /**
     * Human-readable summary of what this entity does.
     */
    String getSummary();

    /**
     * Annotations or decorators on this entity.
     */
    List<String> getAnnotations();

    /**
     * Entity types in the knowledge graph.
     */
    enum EntityType {
        CLASS,
        INTERFACE,
        ENUM,
        RECORD,
        METHOD,
        CONSTRUCTOR,
        FIELD,
        FILE,
        ENDPOINT,
        TEST
    }
}
