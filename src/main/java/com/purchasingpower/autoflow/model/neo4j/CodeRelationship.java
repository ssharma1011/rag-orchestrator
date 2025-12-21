package com.purchasingpower.autoflow.model.neo4j;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a relationship between code entities in Neo4j.
 * Examples: EXTENDS, IMPLEMENTS, CALLS, USES, THROWS, etc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeRelationship {

    /**
     * ID of the source node
     * Example: "CLASS:com.example.PaymentService"
     */
    private String fromId;

    /**
     * ID of the target node
     * Example: "CLASS:com.example.AbstractService"
     */
    private String toId;

    /**
     * Type of relationship
     */
    private RelationType type;

    /**
     * Additional properties for the relationship
     * Example: line number where call occurs, parameter types, etc.
     */
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();

    /**
     * Source file where this relationship originates
     */
    private String sourceFile;

    /**
     * Line number where this relationship occurs
     */
    private Integer lineNumber;

    /**
     * Types of relationships in the code graph
     */
    public enum RelationType {
        // Class-to-Class relationships
        EXTENDS,           // Class extends another class
        IMPLEMENTS,        // Class implements interface
        INNER_CLASS,       // Class contains inner class
        IMPORTS,           // File imports class

        // Class-to-Method/Field relationships
        HAS_METHOD,        // Class has method
        HAS_FIELD,         // Class has field
        HAS_CONSTRUCTOR,   // Class has constructor

        // Method-to-Method relationships
        CALLS,             // Method calls another method
        OVERRIDES,         // Method overrides parent method

        // Method-to-Field relationships
        READS,             // Method reads field
        WRITES,            // Method writes field
        USES,              // Method uses field

        // Method-to-Class relationships
        RETURNS,           // Method returns type
        THROWS,            // Method throws exception
        INSTANTIATES,      // Method creates instance of class

        // Parameter/Variable relationships
        ACCEPTS_PARAMETER, // Method accepts parameter of type
        DECLARES_VARIABLE, // Method declares local variable of type

        // Annotation relationships
        ANNOTATED_WITH,    // Element has annotation

        // Dependency relationships
        DEPENDS_ON,        // Generic dependency
        TYPE_DEPENDENCY    // Has type dependency (field/parameter type)
    }
}
