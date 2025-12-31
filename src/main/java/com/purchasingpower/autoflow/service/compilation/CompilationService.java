package com.purchasingpower.autoflow.service.compilation;

import java.util.List;

/**
 * Compiles Java code in-memory to verify correctness.
 *
 * <p>This service validates that AI-generated code actually compiles
 * before showing it to users, preventing hallucinated or broken code.
 *
 * <p><b>Thread Safety:</b> Implementations must be thread-safe.
 *
 * @since 1.0.0
 */
public interface CompilationService {

    /**
     * Compiles Java source code and returns the result.
     *
     * <p>This method does NOT write compiled classes to disk.
     * Compilation happens entirely in-memory for safety.
     *
     * @param sourceCode The complete Java source code to compile
     * @param className  Fully qualified class name (e.g., "com.example.MyClass")
     * @return Compilation result with errors if compilation failed
     * @throws IllegalArgumentException if sourceCode or className is null/empty
     */
    CompilationResult compile(String sourceCode, String className);

    /**
     * Compiles multiple Java source files together.
     *
     * <p>Useful when generated code spans multiple classes that depend on each other.
     *
     * @param sources Map of className → sourceCode
     * @return Compilation result for all files
     * @throws IllegalArgumentException if sources is null or empty
     */
    CompilationResult compileAll(java.util.Map<String, String> sources);
}
