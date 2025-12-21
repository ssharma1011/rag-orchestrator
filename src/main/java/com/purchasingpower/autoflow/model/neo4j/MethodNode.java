package com.purchasingpower.autoflow.model.neo4j;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Java method in the Neo4j knowledge graph.
 * Captures method signature, parameters, and behavior metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodNode {

    /**
     * Unique ID: "METHOD:com.example.PaymentService.processPayment(Payment)"
     */
    private String id;

    /**
     * Method name: "processPayment"
     */
    private String name;

    /**
     * Fully qualified name including class and signature
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
     * Line range in source file
     */
    private int startLine;
    private int endLine;

    /**
     * Return type (fully qualified)
     * Example: "void", "java.lang.String", "com.example.Payment"
     */
    private String returnType;

    /**
     * Method parameters
     */
    @Builder.Default
    private List<MethodParameter> parameters = new ArrayList<>();

    /**
     * Exceptions thrown by this method
     */
    @Builder.Default
    private List<String> thrownExceptions = new ArrayList<>();

    /**
     * Access modifier: PUBLIC, PRIVATE, PROTECTED, PACKAGE_PRIVATE
     */
    private String accessModifier;

    /**
     * Is this method static?
     */
    private boolean isStatic;

    /**
     * Is this method final?
     */
    private boolean isFinal;

    /**
     * Is this method abstract?
     */
    private boolean isAbstract;

    /**
     * Is this method synchronized?
     */
    private boolean isSynchronized;

    /**
     * Is this a constructor?
     */
    private boolean isConstructor;

    /**
     * Annotations on this method
     */
    @Builder.Default
    private List<String> annotations = new ArrayList<>();

    /**
     * Complete source code of the method
     */
    private String sourceCode;

    /**
     * Javadoc comment (if any)
     */
    private String javadoc;

    /**
     * Cyclomatic complexity (for code analysis)
     */
    private Integer complexity;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MethodParameter {
        private String name;
        private String type;  // Fully qualified type
        private boolean isFinal;
        private List<String> annotations;
    }
}
