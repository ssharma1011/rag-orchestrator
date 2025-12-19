package com.purchasingpower.autoflow.model.library;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single library detection rule.
 * Each rule defines how to identify a specific role/pattern in code.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibraryRule {
    
    /**
     * The role tag assigned when this rule matches.
     * Examples: "spring-kafka:consumer", "spring-data-jpa:repository"
     */
    private String role;
    
    /**
     * Detection patterns for this rule
     */
    private DetectionPattern patterns;
}
