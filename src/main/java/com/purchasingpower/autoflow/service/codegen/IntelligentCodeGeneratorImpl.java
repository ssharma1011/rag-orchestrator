package com.purchasingpower.autoflow.service.codegen;

import com.google.common.base.Preconditions;
import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.service.PromptLibraryService;
import com.purchasingpower.autoflow.service.compilation.CompilationError;
import com.purchasingpower.autoflow.service.compilation.CompilationResult;
import com.purchasingpower.autoflow.service.compilation.CompilationService;
import com.purchasingpower.autoflow.service.search.SearchResult;
import com.purchasingpower.autoflow.service.search.WebSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intelligent code generator with self-correction through compilation feedback.
 *
 * <p><b>How it works:</b>
 * <ol>
 *   <li>Generate code using LLM with coding standards
 *   <li>Compile immediately to catch errors
 *   <li>If compilation fails:
 *     <ul>
 *       <li>Check if error is library-related
 *       <li>Search web for correct API if needed
 *       <li>Feed error + solution back to LLM
 *       <li>Regenerate with corrections
 *     </ul>
 *   <li>Repeat until success or max attempts
 * </ol>
 *
 * <p><b>Thread Safety:</b> This implementation is thread-safe.
 *
 * @since 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IntelligentCodeGeneratorImpl implements IntelligentCodeGenerator {

    private final GeminiClient geminiClient;
    private final CompilationService compilationService;
    private final WebSearchService webSearchService;
    private final PromptLibraryService promptLibrary;

    @Override
    public GeneratedCode generate(CodeGenerationRequest request) {
        validateRequest(request);

        log.info("Generating code for requirement: {}", request.getRequirement());

        // Track whether we used web search (for metrics)
        boolean usedWebSearch = false;

        // Attempt generation with compilation verification
        for (int attempt = 1; attempt <= request.getMaxAttempts(); attempt++) {
            log.debug("Generation attempt {}/{}", attempt, request.getMaxAttempts());

            // Generate code
            String prompt = buildPrompt(request, attempt, null, null);
            String generatedCode = geminiClient.generateText(prompt);

            // Extract class name from generated code
            String className = extractClassName(generatedCode);

            if (className == null) {
                log.warn("Could not extract class name from generated code");
                continue; // Try again
            }

            // Compile to verify correctness
            CompilationResult compilation = compilationService.compile(generatedCode, className);

            log.info(compilation.getSummary());

            if (compilation.isSuccess()) {
                // Success! Return the working code
                return buildSuccessResult(generatedCode, className, attempt, usedWebSearch, compilation);
            }

            // Compilation failed - determine fix strategy
            if (attempt < request.getMaxAttempts()) {
                usedWebSearch = handleCompilationFailure(request, compilation, attempt);
            }
        }

        // All attempts exhausted
        throw new RuntimeException(
                String.format("Failed to generate compilable code after %d attempts", request.getMaxAttempts())
        );
    }

    @Override
    public GeneratedCodeWithTests generateWithTests(CodeGenerationRequest request) {
        // TODO: Implement test-driven generation
        // 1. Generate tests first
        // 2. Compile tests
        // 3. Generate implementation
        // 4. Run tests
        // 5. Fix until tests pass

        throw new UnsupportedOperationException("Test-driven generation not yet implemented");
    }

    /**
     * Validates the generation request.
     *
     * <p>Fails fast if request is invalid.
     */
    private void validateRequest(CodeGenerationRequest request) {
        Preconditions.checkNotNull(request, "Request cannot be null");
        Preconditions.checkNotNull(request.getRequirement(), "Requirement cannot be null");
        Preconditions.checkArgument(!request.getRequirement().isEmpty(), "Requirement cannot be empty");
        Preconditions.checkArgument(request.getMaxAttempts() > 0, "Max attempts must be > 0");
    }

    /**
     * Builds the prompt for code generation.
     *
     * <p>Includes coding standards, context, and any error feedback from previous attempts.
     */
    private String buildPrompt(CodeGenerationRequest request, int attempt,
                                CompilationResult previousFailure, SearchResult webSearch) {

        Map<String, Object> context = new HashMap<>();
        context.put("requirement", request.getRequirement());
        context.put("repoName", request.getRepoName());
        context.put("existingClasses", String.join(", ", request.getExistingClasses()));
        context.put("constraints", String.join("\n- ", request.getConstraints()));

        // On retries, include error feedback
        if (attempt > 1 && previousFailure != null) {
            context.put("previousError", previousFailure.getDetailedErrors());

            if (webSearch != null && webSearch.getConfidence() > 0) {
                context.put("webSearchResults", webSearch.formatForLLM());
            }
        }

        // Use prompt template (externalized, not hardcoded)
        return promptLibrary.render("intelligent-code-gen", context);
    }

    /**
     * Handles compilation failure by searching for solutions.
     *
     * <p>Returns true if web search was used.
     */
    private boolean handleCompilationFailure(CodeGenerationRequest request,
                                              CompilationResult compilation, int attempt) {

        log.warn("Compilation failed on attempt {}. Analyzing errors...", attempt);

        // Check if any error is likely a library version mismatch
        boolean foundLibraryError = compilation.getErrors().stream()
                .anyMatch(CompilationError::isLikelyVersionMismatch);

        if (foundLibraryError && webSearchService.isEnabled()) {
            log.info("Detected library version mismatch. Searching web for solution...");

            // Extract library name from first error
            CompilationError firstError = compilation.getErrors().get(0);
            String libraryName = firstError.extractLibraryName();

            if (libraryName != null) {
                String searchQuery = String.format("%s Java %s fix 2024",
                        libraryName, firstError.getMessage());

                SearchResult webSearch = webSearchService.search(searchQuery);

                if (webSearch.getConfidence() > 50) {
                    log.info("Found web search solution with {}% confidence", webSearch.getConfidence());
                    return true; // Used web search
                }
            }
        } else {
            log.debug("Not a library error or web search disabled. LLM will try to self-correct.");
        }

        return false; // Did not use web search
    }

    /**
     * Extracts the fully qualified class name from generated code.
     *
     * <p>Looks for pattern: public class ClassName
     * and combines with package declaration.
     */
    private String extractClassName(String code) {
        // Extract package
        Pattern packagePattern = Pattern.compile("package\\s+([a-zA-Z0-9.]+)\\s*;");
        Matcher packageMatcher = packagePattern.matcher(code);
        String packageName = packageMatcher.find() ? packageMatcher.group(1) : "";

        // Extract class name
        Pattern classPattern = Pattern.compile("public\\s+(class|interface|enum)\\s+([A-Z][a-zA-Z0-9]*)");
        Matcher classMatcher = classPattern.matcher(code);

        if (classMatcher.find()) {
            String simpleName = classMatcher.group(2);
            return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        }

        return null;
    }

    /**
     * Builds successful generation result.
     */
    private GeneratedCode buildSuccessResult(String code, String className, int attempts,
                                              boolean usedWebSearch, CompilationResult compilation) {

        log.info("✅ Successfully generated compilable code on attempt {}", attempts);

        return GeneratedCode.builder()
                .sourceCode(code)
                .className(className)
                .packageName(GeneratedCode.extractPackageName(className))
                .attempts(attempts)
                .usedWebSearch(usedWebSearch)
                .compilationResult(compilation)
                .build();
    }
}
