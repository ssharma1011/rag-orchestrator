package com.purchasingpower.autoflow.model.java;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * Represents a parsed Java class/interface/enum.
 *
 * @since 2.0.0
 */
@Value
@Builder
public class JavaClass {
    String id;
    String repositoryId;
    String name;
    String packageName;
    String fullyQualifiedName;
    String filePath;
    int startLine;
    int endLine;
    JavaTypeKind kind; // CLASS, INTERFACE, ENUM
    List<String> annotations;
    String extendsClass;
    List<String> implementsInterfaces;
    List<JavaMethod> methods;
    List<JavaField> fields;
    String description; // Enriched text for embedding
    List<Double> embedding; // Vector embedding (1024 dims for mxbai-embed-large)
}
