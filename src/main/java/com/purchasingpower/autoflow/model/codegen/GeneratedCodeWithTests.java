package com.purchasingpower.autoflow.model.codegen;

import com.purchasingpower.autoflow.model.llm.CodeGenerationResponse;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Result of intelligent code generation with compiled code and generated tests.
 *
 * <p>This model represents the output of the IntelligentCodeGenerator's
 * compilation-in-the-loop process, which generates code, compiles it,
 * fixes errors, and generates corresponding unit tests.
 *
 * <p><b>Usage:</b>
 * <pre>
 * GeneratedCodeWithTests result = intelligentCodeGenerator.generateWithCompilation(
 *     requirement, context, scope, conversationId
 * );
 *
 * if (result.isCompiled()) {
 *     // Code compiled successfully
 *     CodeGenerationResponse generatedCode = result.getGeneratedCode();
 *     List&lt;String&gt; testFiles = result.getTestFiles();
 * }
 * </pre>
 *
 * @author AutoFlow Pipeline
 * @since 1.0.0
 */
@Value
@Builder
public class GeneratedCodeWithTests {

    /**
     * The generated code (file edits, implementation plan, etc.).
     */
    CodeGenerationResponse generatedCode;

    /**
     * List of generated test file paths.
     * Each entry is a relative path like "src/test/java/com/example/UserServiceTest.java"
     */
    List<String> testFiles;

    /**
     * Whether the generated code compiled successfully.
     */
    boolean compiled;

    /**
     * Number of compilation attempts made before success.
     * If compiled=false, this is the max attempts that were tried.
     */
    int compilationAttempts;

    /**
     * Final compilation errors if compilation failed.
     * Empty if compiled=true.
     */
    String compilationErrors;

    /**
     * Confidence score from LLM code generation (0.0 to 1.0).
     * Reflects how confident the LLM is in the generated code.
     */
    double confidence;

    /**
     * Check if code generation was successful and code compiles.
     *
     * @return true if code compiled successfully
     */
    public boolean isSuccess() {
        return compiled && generatedCode != null;
    }

    /**
     * Get number of test files generated.
     *
     * @return count of test files, or 0 if none
     */
    public int getTestFileCount() {
        return testFiles != null ? testFiles.size() : 0;
    }
}
