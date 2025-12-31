package com.purchasingpower.autoflow.service.validation;

import java.util.List;

/**
 * Validates generated code against coding standards.
 *
 * <p>Checks compliance with rules from CODING_STANDARDS.md including:
 * <ul>
 *   <li>Method length limits
 *   <li>Cyclomatic complexity
 *   <li>Parameter count
 *   <li>If-else chain detection
 *   <li>Interface + implementation pattern
 * </ul>
 *
 * @since 1.0.0
 */
public interface CodeQualityValidator {

    /**
     * Validates code quality against standards.
     *
     * @param sourceCode Java source code to validate
     * @return Validation result with any violations found
     * @throws IllegalArgumentException if sourceCode is null or empty
     */
    ValidationResult validate(String sourceCode);

    /**
     * Quick validation focusing on critical violations only.
     *
     * <p>Faster than full validation, checks only:
     * <ul>
     *   <li>Method length > 20
     *   <li>Complexity > 10
     *   <li>If-else chains >= 3
     * </ul>
     *
     * @param sourceCode Java source code
     * @return true if critical violations found, false otherwise
     */
    boolean hasCriticalViolations(String sourceCode);
}
