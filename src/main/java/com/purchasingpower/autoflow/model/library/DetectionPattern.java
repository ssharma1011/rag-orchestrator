package com.purchasingpower.autoflow.model.library;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines patterns for detecting a specific role in code.
 * Supports multiple matching strategies: exact match, prefix, regex.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetectionPattern {
    
    /**
     * Required import statements (fully qualified names).
     * Example: ["org.springframework.kafka.annotation.KafkaListener"]
     */
    @Builder.Default
    private List<String> imports = new ArrayList<>();
    
    /**
     * Required import prefixes (package-level matching).
     * Example: ["org.springframework.kafka"] matches all Kafka imports
     */
    @Builder.Default
    private List<String> importPrefixes = new ArrayList<>();
    
    /**
     * Required annotations (simple names).
     * Example: ["@KafkaListener", "@Service"]
     */
    @Builder.Default
    private List<String> annotations = new ArrayList<>();
    
    /**
     * Required interfaces (fully qualified names).
     * Example: ["org.springframework.data.repository.CrudRepository"]
     */
    @Builder.Default
    private List<String> interfaces = new ArrayList<>();
    
    /**
     * Required class names (simple names) in imports.
     * Example: ["KafkaTemplate", "WebClient"]
     */
    @Builder.Default
    private List<String> classes = new ArrayList<>();
    
    /**
     * Required method calls detected in code.
     * Example: ["kafkaTemplate.send", "webClient.post"]
     */
    @Builder.Default
    private List<String> methodCalls = new ArrayList<>();
    
    /**
     * Required superclass (fully qualified name).
     * Example: "org.springframework.boot.SpringApplication"
     */
    private String extendsClass;
    
    /**
     * Pattern matching mode: ALL (all patterns must match) or ANY (at least one pattern)
     */
    @Builder.Default
    private MatchMode mode = MatchMode.ALL;
    
    public enum MatchMode {
        /** All patterns must match (AND logic) */
        ALL,
        /** At least one pattern must match (OR logic) */
        ANY
    }
}
