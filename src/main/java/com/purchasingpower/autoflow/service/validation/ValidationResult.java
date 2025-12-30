package com.purchasingpower.autoflow.service.validation;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Result of code quality validation.
 *
 * <p>Contains all violations found during validation.
 * Immutable and thread-safe.
 *
 * @since 1.0.0
 */
@Value
@Builder
public class ValidationResult {
    boolean valid;
    List<Violation> violations;
    String summary;

    /**
     * Creates a successful validation result with no violations.
     */
    public static ValidationResult success() {
        return ValidationResult.builder()
                .valid(true)
                .violations(List.of())
                .summary("✅ No violations found")
                .build();
    }

    /**
     * Creates a failed validation result with violations.
     */
    public static ValidationResult failure(List<Violation> violations) {
        return ValidationResult.builder()
                .valid(false)
                .violations(violations)
                .summary(String.format("❌ Found %d violations", violations.size()))
                .build();
    }

    /**
     * Single validation violation.
     */
    @Value
    @Builder
    public static class Violation {
        String rule;        // e.g., "max-method-lines"
        String severity;    // "error" or "warning"
        String location;    // e.g., "UserService.createUser() at line 42"
        String message;     // e.g., "Method has 25 lines (max is 20)"
        String suggestion;  // e.g., "Split into smaller methods"
    }
}
