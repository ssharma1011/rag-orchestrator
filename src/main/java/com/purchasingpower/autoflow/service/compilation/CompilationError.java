package com.purchasingpower.autoflow.service.compilation;

import lombok.Builder;
import lombok.Value;

/**
 * Represents a single compilation error.
 *
 * <p>Contains enough detail for the LLM to understand and fix the error.
 *
 * @since 1.0.0
 */
@Value
@Builder
public class CompilationError {

    /**
     * Line number where error occurred (1-indexed).
     */
    int line;

    /**
     * Column number where error occurred (1-indexed).
     */
    int column;

    /**
     * Human-readable error message.
     *
     * <p>Examples:
     * - "cannot find symbol: variable graphTraversal"
     * - "incompatible types: TransactionContext cannot be converted to Transaction"
     */
    String message;

    /**
     * The problematic code snippet (optional).
     *
     * <p>Helps LLM see exactly what caused the error.
     */
    String code;

    /**
     * Error kind (e.g., "SYMBOL_NOT_FOUND", "TYPE_MISMATCH").
     *
     * <p>Used to categorize errors and potentially search for solutions.
     */
    String kind;

    /**
     * Extracts library name from error message if it's a library-related error.
     *
     * <p>Examples:
     * - "cannot find symbol: Transaction" → "Neo4j" (if Transaction is from Neo4j)
     * - "package org.springframework.web does not exist" → "Spring"
     *
     * @return Library name or null if not a library error
     */
    public String extractLibraryName() {
        // Common library patterns
        if (message.contains("org.springframework")) return "Spring";
        if (message.contains("org.neo4j")) return "Neo4j";
        if (message.contains("com.google.cloud")) return "Google Cloud";

        // Check for class names that might indicate library
        if (message.contains("Transaction") && message.contains("cannot find symbol")) {
            return "Neo4j"; // Common Neo4j class
        }

        return null;
    }

    /**
     * Determines if this error is likely due to wrong library version.
     *
     * <p>Indicators:
     * - "cannot find symbol" for known library classes
     * - "incompatible types" between similar class names
     * - "has been deprecated" warnings
     */
    public boolean isLikelyVersionMismatch() {
        return message.contains("cannot find symbol")
                || message.contains("incompatible types")
                || message.contains("deprecated")
                || message.contains("cannot be converted to");
    }
}
