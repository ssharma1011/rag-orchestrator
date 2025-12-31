package com.purchasingpower.autoflow.service.codegen;

import com.purchasingpower.autoflow.model.codegen.GeneratedCodeWithTests;

/**
 * Generates code with built-in self-correction through compilation feedback.
 *
 * <p>This generator:
 * <ol>
 *   <li>Generates code using LLM
 *   <li>Compiles it immediately to verify correctness
 *   <li>If compilation fails, searches web for solutions
 *   <li>Regenerates with corrected context
 *   <li>Repeats until code compiles or max attempts reached
 * </ol>
 *
 * <p><b>Why this matters:</b> Prevents showing users broken code that doesn't compile.
 *
 * <p><b>Thread Safety:</b> Implementations must be thread-safe.
 *
 * @since 1.0.0
 */
public interface IntelligentCodeGenerator {

    /**
     * Generates compilable Java code for a requirement.
     *
     * <p>Unlike standard LLM generation, this method GUARANTEES
     * the returned code compiles (or throws exception if impossible).
     *
     * @param request Code generation request with requirement and context
     * @return Generated code that successfully compiles
     * @throws CodeGenerationException if cannot generate compilable code after max attempts
     */
    GeneratedCode generate(CodeGenerationRequest request);

    /**
     * Generates code with tests.
     *
     * <p>Follows test-driven approach:
     * <ol>
     *   <li>Generates tests first
     *   <li>Generates implementation
     *   <li>Verifies tests pass
     * </ol>
     *
     * @param request Code generation request
     * @return Generated code with passing tests
     * @throws CodeGenerationException if tests don't pass after max attempts
     */
    GeneratedCodeWithTests generateWithTests(CodeGenerationRequest request);
}
