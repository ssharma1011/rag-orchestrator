package com.purchasingpower.autoflow.service.compilation;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Immutable result of a compilation attempt.
 *
 * <p>Contains either success status or detailed error information
 * that can be fed back to the LLM for self-correction.
 *
 * @since 1.0.0
 */
@Value
@Builder
public class CompilationResult {

    /**
     * Whether compilation succeeded.
     */
    boolean success;

    /**
     * Compilation errors (empty if success = true).
     *
     * <p>Errors include line numbers and helpful messages
     * that can be used to guide code regeneration.
     */
    @Builder.Default
    List<CompilationError> errors = List.of();

    /**
     * Compilation warnings (even if success = true).
     *
     * <p>Warnings indicate code smells that should be fixed,
     * such as unused variables or deprecated API usage.
     */
    @Builder.Default
    List<String> warnings = List.of();

    /**
     * Time taken to compile in milliseconds.
     */
    long compilationTimeMs;

    /**
     * Creates a successful compilation result.
     */
    public static CompilationResult success(long timeMs) {
        return CompilationResult.builder()
                .success(true)
                .compilationTimeMs(timeMs)
                .build();
    }

    /**
     * Creates a failed compilation result.
     */
    public static CompilationResult failure(List<CompilationError> errors, long timeMs) {
        return CompilationResult.builder()
                .success(false)
                .errors(errors)
                .compilationTimeMs(timeMs)
                .build();
    }

    /**
     * Returns a human-readable summary for logging.
     */
    public String getSummary() {
        if (success) {
            return String.format("✅ Compilation succeeded in %dms", compilationTimeMs);
        } else {
            return String.format("❌ Compilation failed with %d errors in %dms",
                    errors.size(), compilationTimeMs);
        }
    }

    /**
     * Returns detailed error messages for LLM feedback.
     *
     * <p>Format optimized for LLM understanding:
     * - Clear error descriptions
     * - Line numbers for context
     * - Actionable fix suggestions
     */
    public String getDetailedErrors() {
        if (success) {
            return "No errors";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Compilation failed with ").append(errors.size()).append(" errors:\n\n");

        for (int i = 0; i < errors.size(); i++) {
            CompilationError error = errors.get(i);
            sb.append(String.format("%d. Line %d: %s\n",
                    i + 1, error.getLine(), error.getMessage()));

            if (error.getCode() != null) {
                sb.append("   Code: ").append(error.getCode()).append("\n");
            }
        }

        return sb.toString();
    }
}
