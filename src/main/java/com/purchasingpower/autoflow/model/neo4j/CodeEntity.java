package com.purchasingpower.autoflow.model.neo4j;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base class for all code entities stored in Neo4j.
 * Represents a node in the knowledge graph.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeEntity {

    /**
     * Unique identifier for this entity.
     * Format: {entityType}:{fullyQualifiedName}
     * Example: "CLASS:com.example.PaymentService"
     */
    private String id;

    /**
     * Type of entity: CLASS, INTERFACE, METHOD, FIELD, PACKAGE
     */
    private EntityType entityType;

    /**
     * Simple name of the entity.
     * Example: "PaymentService" for a class
     */
    private String name;

    /**
     * Fully qualified name including package.
     * Example: "com.example.PaymentService"
     */
    private String fullyQualifiedName;

    /**
     * Source file path where this entity is defined.
     */
    private String sourceFilePath;

    /**
     * Line number where this entity starts.
     */
    private int startLine;

    /**
     * Line number where this entity ends.
     */
    private int endLine;

    /**
     * Source code snippet for this entity.
     */
    private String sourceCode;

    public enum EntityType {
        CLASS,
        INTERFACE,
        ENUM,
        METHOD,
        FIELD,
        PACKAGE,
        ANNOTATION
    }
}
