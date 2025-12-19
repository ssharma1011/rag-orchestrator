package com.purchasingpower.autoflow.model.library;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a complete library/framework definition.
 * Contains base identification info and specific role detection rules.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibraryDefinition {
    
    /**
     * Display name of the library.
     * Example: "Spring Framework", "Apache Kafka"
     */
    private String name;
    
    /**
     * Base package for general detection.
     * Example: "org.springframework", "org.apache.kafka"
     */
    private String basePackage;
    
    /**
     * Supported file extensions.
     * Example: [".java"] for Java, [".ts", ".js"] for TypeScript/JavaScript
     */
    @Builder.Default
    private List<String> fileExtensions = List.of(".java");
    
    /**
     * Project type this library belongs to.
     * Example: JAVA_SPRING, ANGULAR, HYBRIS
     */
    private ProjectType projectType;
    
    /**
     * Specific role detection rules for this library.
     */
    @Builder.Default
    private List<LibraryRule> rules = new ArrayList<>();
    
    public enum ProjectType {
        JAVA_SPRING,
        JAVA_GENERIC,
        ANGULAR,
        REACT,
        HYBRIS,
        PYTHON,
        UNKNOWN
    }
}
