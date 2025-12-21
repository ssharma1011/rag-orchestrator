package com.purchasingpower.autoflow.model.neo4j;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Java class/interface in the Neo4j knowledge graph.
 * Stores metadata about class structure and relationships.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassNode {

    /**
     * Unique ID: "CLASS:com.example.PaymentService"
     */
    private String id;

    /**
     * Simple class name: "PaymentService"
     */
    private String name;

    /**
     * Fully qualified name: "com.example.PaymentService"
     */
    private String fullyQualifiedName;

    /**
     * Package name: "com.example"
     */
    private String packageName;

    /**
     * Source file path
     */
    private String sourceFilePath;

    /**
     * Line range in source file
     */
    private int startLine;
    private int endLine;

    /**
     * Class type: CLASS, INTERFACE, ENUM, ABSTRACT_CLASS
     */
    private ClassType classType;

    /**
     * Fully qualified name of superclass (if any)
     */
    private String superClassName;

    /**
     * List of implemented interfaces (fully qualified names)
     */
    @Builder.Default
    private List<String> interfaces = new ArrayList<>();

    /**
     * Access modifier: PUBLIC, PRIVATE, PROTECTED, PACKAGE_PRIVATE
     */
    private String accessModifier;

    /**
     * Is this class abstract?
     */
    private boolean isAbstract;

    /**
     * Is this class final?
     */
    private boolean isFinal;

    /**
     * Is this class static?
     */
    private boolean isStatic;

    /**
     * Annotations on this class
     */
    @Builder.Default
    private List<String> annotations = new ArrayList<>();

    /**
     * Complete source code of the class
     */
    private String sourceCode;

    /**
     * Javadoc comment (if any)
     */
    private String javadoc;

    public enum ClassType {
        CLASS,
        INTERFACE,
        ENUM,
        ABSTRACT_CLASS,
        ANNOTATION
    }
}
