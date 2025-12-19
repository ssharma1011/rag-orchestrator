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
 * Used to create the "parent" chunk in hierarchical vector storage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassMetadata {

    private String fullyQualifiedName;
    private String packageName;
    private String className;
    private String domain;
    private String businessCapability;
    private List<String> features;
    private List<String> concepts;

    @Builder.Default
    private List<String> annotations = new ArrayList<>();

    @Builder.Default
    private List<String> implementedInterfaces = new ArrayList<>();

    private String superClass;

    @Builder.Default
    private List<String> importedClasses = new ArrayList<>();

    @Builder.Default
    private List<String> usedLibraries = new ArrayList<>();

    /**
     * PRODUCTION-GRADE GRAPH EDGES:
     * Detailed relationships including Type and Cardinality.
     * Ready for Graph DB export.
     */
    @Builder.Default
    private Set<DependencyEdge> dependencies = new HashSet<>();

    @Builder.Default
    private List<String> innerClasses = new ArrayList<>();

    /**
     * NEW: Roles detected based on annotations/imports (e.g., "spring-kafka:consumer")
     */
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