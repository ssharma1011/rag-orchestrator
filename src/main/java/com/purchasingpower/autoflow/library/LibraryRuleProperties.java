package com.purchasingpower.autoflow.library;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps the entire library-rules.yml to a Java object structure.
 */
@Data
@Component
@ConfigurationProperties(prefix = "autoflow.library-rules")
public class LibraryRuleProperties {
    private List<Library> libraries;

    @Data
    public static class Library {
        private String name;
        private String basePackage;
        private List<Rule> rules;
    }

    @Data
    public static class Rule {
        private String role;
        private DetectionPatterns patterns; // Nested map for imports/annotations
    }

    @Data
    public static class DetectionPatterns {
        // List of fully qualified imports or package prefixes
        private List<String> imports = List.of();
        // List of simple class names to look for in imports
        private List<String> classes = List.of();
        // List of annotations to look for
        private List<String> annotations = List.of();
        // List of interfaces the class must implement/extend
        private List<String> interfaces = List.of();
    }
}