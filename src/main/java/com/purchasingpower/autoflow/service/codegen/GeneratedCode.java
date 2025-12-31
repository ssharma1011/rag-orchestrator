package com.purchasingpower.autoflow.service.codegen;

import com.purchasingpower.autoflow.service.compilation.CompilationResult;
import lombok.Builder;
import lombok.Value;

/**
 * Immutable generated code that has been verified to compile.
 *
 * @since 1.0.0
 */
@Value
@Builder
public class GeneratedCode {

    /**
     * The Java source code.
     */
    String sourceCode;

    /**
     * Fully qualified class name.
     */
    String className;

    /**
     * Package name extracted from class name.
     */
    String packageName;

    /**
     * Number of attempts required to generate compilable code.
     */
    int attempts;

    /**
     * Whether web search was used to fix errors.
     */
    boolean usedWebSearch;

    /**
     * Final compilation result (should always be success).
     */
    CompilationResult compilationResult;

    /**
     * Extracts package name from fully qualified class name.
     */
    public static String extractPackageName(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(0, lastDot) : "";
    }
}
