package com.purchasingpower.autoflow.service.codegen;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Immutable request for code generation.
 *
 * <p>Contains all context needed for the LLM to generate correct code.
 *
 * @since 1.0.0
 */
@Value
@Builder
public class CodeGenerationRequest {

    /**
     * What the user wants the code to do.
     *
     * <p>Example: "Create a service that validates user emails"
     */
    String requirement;

    /**
     * Repository name for context.
     */
    String repoName;

    /**
     * Existing classes in the codebase that can be referenced.
     *
     * <p>Helps LLM generate code that integrates properly.
     */
    @Builder.Default
    List<String> existingClasses = List.of();

    /**
     * Existing code snippets for context.
     *
     * <p>Map of className → code snippet
     */
    @Builder.Default
    Map<String, String> codeContext = Map.of();

    /**
     * Additional constraints or requirements.
     *
     * <p>Examples:
     * - "Must use Spring Boot"
     * - "Follow repository pattern"
     * - "Include input validation"
     */
    @Builder.Default
    List<String> constraints = List.of();

    /**
     * Maximum attempts to generate compilable code.
     *
     * <p>Default: 3 attempts
     */
    @Builder.Default
    int maxAttempts = 3;

    /**
     * Whether to include tests in generation.
     */
    @Builder.Default
    boolean includeTests = false;
}
