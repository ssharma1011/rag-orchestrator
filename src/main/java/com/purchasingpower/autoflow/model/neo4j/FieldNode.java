package com.purchasingpower.autoflow.model.neo4j;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Java field in the Neo4j knowledge graph.
 * Captures field type, modifiers, and initialization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldNode {

    /**
     * Unique ID: "FIELD:com.example.PaymentService.stripeGateway"
     */
    private String id;

    /**
     * Field name: "stripeGateway"
     */
    private String name;

    /**
     * Fully qualified name including class
     */
    private String fullyQualifiedName;

    /**
     * Containing class fully qualified name
     */
    private String className;

    /**
     * Source file path
     */
    private String sourceFilePath;

    /**
     * Line number where field is declared
     */
    private int lineNumber;

    /**
     * Field type (fully qualified)
     * Example: "java.lang.String", "com.example.StripeGateway"
     */
    private String type;

    /**
     * Access modifier: PUBLIC, PRIVATE, PROTECTED, PACKAGE_PRIVATE
     */
    private String accessModifier;

    /**
     * Is this field static?
     */
    private boolean isStatic;

    /**
     * Is this field final?
     */
    private boolean isFinal;

    /**
     * Is this field transient?
     */
    private boolean isTransient;

    /**
     * Is this field volatile?
     */
    private boolean isVolatile;

    /**
     * Annotations on this field
     */
    @Builder.Default
    private List<String> annotations = new ArrayList<>();

    /**
     * Initial value (if any)
     */
    private String initialValue;

    /**
     * Javadoc comment (if any)
     */
    private String javadoc;
}
