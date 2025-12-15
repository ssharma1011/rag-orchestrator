package com.purchasingpower.autoflow.model.ast;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Metadata extracted from a Java class/interface/enum during AST parsing.
 * Used to create the "parent" chunk in hierarchical vector storage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassMetadata {

    /**
     * Fully qualified name: com.purchasingpower.autoflow.client.GeminiClient
     */
    private String fullyQualifiedName;

    /**
     * Package name: com.purchasingpower.autoflow.client
     */
    private String packageName;

    /**
     * Simple class name: GeminiClient
     */
    private String className;

    /**
     * Annotations present on the class: [@Component, @Slf4j, @RequiredArgsConstructor]
     */
    @Builder.Default
    private List<String> annotations = new ArrayList<>();

    /**
     * Interfaces implemented by this class: [ClientInterface, Retryable]
     */
    @Builder.Default
    private List<String> implementedInterfaces = new ArrayList<>();

    /**
     * Superclass (if extends something): BaseClient, Object, etc.
     */
    private String superClass;

    /**
     * All import statements from the file (for library detection)
     * Example: [org.springframework.stereotype.Component, lombok.extern.slf4j.Slf4j]
     */
    @Builder.Default
    private List<String> importedClasses = new ArrayList<>();

    /**
     * High-level libraries detected from imports (Spring, Lombok, JPA, etc.)
     * Derived from importedClasses during parsing
     */
    @Builder.Default
    private List<String> usedLibraries = new ArrayList<>();

    /**
     * Whether the class is abstract
     */
    private boolean isAbstract;

    /**
     * Whether this is an interface
     */
    private boolean isInterface;

    /**
     * Whether this is an enum
     */
    private boolean isEnum;

    /**
     * Total line count of the file
     */
    private int lineCount;

    /**
     * Relative path from project root: src/main/java/com/purchasingpower/autoflow/client/GeminiClient.java
     */
    private String sourceFilePath;

    /**
     * Brief description of what this class does (for embedding)
     * Generated from: JavaDoc comment, class name analysis, field names
     */
    private String classSummary;
}