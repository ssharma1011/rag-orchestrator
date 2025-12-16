package com.purchasingpower.autoflow.model.ast;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a rich relationship between two classes.
 * Designed for Property Graph databases (Neo4j, Oracle Graph).
 */
@Data
@Builder
public class DependencyEdge {

    /**
     * The fully qualified name of the target class.
     * Example: "com.purchasingpower.service.UserService"
     */
    private String targetClass;

    /**
     * How the dependency is used.
     */
    private RelationshipType type;

    /**
     * The cardinality of the relationship.
     * ONE: "User user"
     * MANY: "List<User> users"
     */
    private Cardinality cardinality;

    /**
     * Additional context about the usage.
     * - Field Name (for composition)
     * - Method Name (for calls)
     */
    private String context;

    /**
     * Enum for the type of relationship.
     * Kept inner for cohesion as it is specific to this edge model.
     */
    public enum RelationshipType {
        INJECTS,      // Field injection or Constructor parameter
        RETURNS,      // Method return type
        ACCEPTS,      // Method parameter
        THROWS,       // Exception thrown
        EXTENDS,      // Superclass
        IMPLEMENTS,   // Interface
        USES          // Local variable or static call
    }

    /**
     * Enum for cardinality.
     */
    public enum Cardinality {
        ONE,
        MANY
    }

    /**
     * Returns a string representation for Pinecone metadata.
     * Format: TYPE:TargetClass
     * Example: "INJECTS:com.purchasingpower.service.UserService"
     */
    public String toMetadataString() {
        return type.name() + ":" + targetClass;
    }
}