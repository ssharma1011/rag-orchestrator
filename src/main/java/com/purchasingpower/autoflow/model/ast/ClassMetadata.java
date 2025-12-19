package com.purchasingpower.autoflow.model.ast;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Metadata extracted from a Java class/interface/enum during AST parsing.
 * Includes knowledge graph fields for semantic domain analysis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassMetadata {

    private String fullyQualifiedName;
    private String packageName;
    private String className;

    // ================================================================
    // KNOWLEDGE GRAPH FIELDS (NEW)
    // ================================================================

    /**
     * Business domain this class belongs to.
     * Examples: "payment", "user-management", "inventory", "shipping"
     * Inferred from: package name, class name, imports
     */
    private String domain;

    /**
     * Business capability this class provides.
     * Examples: "transaction-processing", "authentication", "notification"
     * Inferred from: annotations, interfaces, method names
     */
    private String businessCapability;

    /**
     * Specific features this class implements.
     * Examples: ["checkout", "refunds", "fraud-detection"]
     * Inferred from: method names, comments, annotations
     */
    @Builder.Default
    private List<String> features = new ArrayList<>();

    /**
     * Domain concepts this class deals with.
     * Examples: ["financial", "PCI-compliant", "transactional", "async"]
     * Inferred from: annotations, interfaces, field types
     */
    @Builder.Default
    private List<String> concepts = new ArrayList<>();

    // ================================================================
    // EXISTING FIELDS
    // ================================================================

    @Builder.Default
    private List<String> annotations = new ArrayList<>();

    @Builder.Default
    private List<String> implementedInterfaces = new ArrayList<>();

    private String superClass;

    @Builder.Default
    private List<String> importedClasses = new ArrayList<>();

    @Builder.Default
    private List<String> usedLibraries = new ArrayList<>();

    @Builder.Default
    private Set<DependencyEdge> dependencies = new HashSet<>();

    @Builder.Default
    private List<String> innerClasses = new ArrayList<>();

    @Builder.Default
    private List<String> roles = new ArrayList<>();

    private boolean isAbstract;
    private boolean isInterface;
    private boolean isEnum;
    private boolean isInnerClass;
    private int lineCount;
    private String sourceFilePath;
    private String classSummary;
}