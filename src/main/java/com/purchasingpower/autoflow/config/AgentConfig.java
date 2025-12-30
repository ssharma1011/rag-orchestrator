package com.purchasingpower.autoflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Umbrella configuration class for all agent-specific settings.
 *
 * <p>This configuration aggregates settings for all workflow agents including requirement
 * analysis, context building, build validation, code review, documentation generation,
 * and test execution. Each agent has its own nested configuration class with agent-specific
 * parameters.
 *
 * <p>Properties are loaded from the {@code app.agents} namespace in application.yml.
 * Example configuration:
 * <pre>
 * app:
 *   agents:
 *     requirement-analyzer:
 *       min-confidence: 0.7
 *     context-builder:
 *       min-confidence: 0.9
 *       ask-user-below-threshold: true
 *       max-token-budget: 10000
 *     build:
 *       max-retry-attempts: 3
 *       timeout-minutes: 10
 *     code-review:
 *       max-retry-attempts: 2
 *     documentation:
 *       max-code-matches: 10
 *       max-fallback-nodes: 20
 *       max-log-preview: 5
 *     test-runner:
 *       shell-windows: "cmd.exe"
 *       shell-flag-windows: "/c"
 *       shell-unix: "sh"
 *       shell-flag-unix: "-c"
 *       test-command: "mvn -B test"
 *       timeout-minutes: 30
 * </pre>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe as Spring manages a single instance
 * and all fields are effectively immutable after initialization.
 *
 * @author AutoFlow Pipeline
 * @since 1.0.0
 */
@Component
@ConfigurationProperties(prefix = "app.agents")
@Data
public class AgentConfig {

    /**
     * Configuration for the Requirement Analyzer agent.
     * Analyzes user requirements to extract structured scope information.
     */
    private RequirementAnalyzerConfig requirementAnalyzer;

    /**
     * Configuration for the Context Builder agent.
     * Builds context from discovered code for code generation.
     */
    private ContextBuilderConfig contextBuilder;

    /**
     * Configuration for the Build validator agent.
     * Validates generated code by attempting to build/compile it.
     */
    private BuildConfig build;

    /**
     * Configuration for the Code Review agent.
     * Reviews and validates generated code quality.
     */
    private CodeReviewConfig codeReview;

    /**
     * Configuration for the Documentation agent.
     * Generates documentation for generated code.
     */
    private DocumentationConfig documentation;

    /**
     * Configuration for the Test Runner agent.
     * Executes tests to validate generated code behavior.
     */
    private TestRunnerConfig testRunner;

    /**
     * Configuration for the Requirement Analyzer Agent.
     *
     * <p>This agent parses user requirements and classifies them into structured
     * components. It uses confidence thresholds to determine when human review
     * is needed.
     */
    @Data
    public static class RequirementAnalyzerConfig {

        /**
         * Minimum confidence score (0.0-1.0) to accept requirement analysis.
         * If confidence falls below this, the analysis should be reviewed by the user.
         * Default: 0.7
         */
        private double minConfidence;
    }

    /**
     * Configuration for the Context Builder Agent.
     *
     * <p>This agent discovers and assembles relevant code context from the repository
     * for use in code generation. It manages token budgets and confidence thresholds.
     */
    @Data
    public static class ContextBuilderConfig {

        /**
         * Minimum confidence score (0.0-1.0) for context assembly.
         * If confidence falls below this, user verification is required.
         * Default: 0.9
         */
        private double minConfidence;

        /**
         * Whether to ask the user for confirmation when confidence falls below threshold.
         * If false, the system proceeds with best-effort context.
         * Default: true
         */
        private boolean askUserBelowThreshold;

        /**
         * Maximum number of tokens to include in the assembled context.
         * Prevents the context from being too large for the generative model.
         * Typical Gemini models support 100K+ tokens, but smaller values allow
         * room for the generated response.
         * Default: 10000
         */
        private int maxTokenBudget;
    }

    /**
     * Configuration for the Build Validator Agent.
     *
     * <p>This agent attempts to compile/build generated code to verify it works
     * in the target repository. It handles retry logic and timeout behavior.
     */
    @Data
    public static class BuildConfig {

        /**
         * Maximum number of retry attempts if build fails.
         * Each retry may involve regenerating code or adjusting compilation settings.
         * Default: 3
         */
        private int maxRetryAttempts;

        /**
         * Maximum time in minutes to wait for a single build to complete.
         * Prevents builds from hanging indefinitely.
         * Default: 10
         */
        private int timeoutMinutes;
    }

    /**
     * Configuration for the Code Review Agent.
     *
     * <p>This agent reviews generated code for quality, style, and adherence to
     * coding standards. It can trigger regeneration on review failures.
     */
    @Data
    public static class CodeReviewConfig {

        /**
         * Maximum number of code regeneration attempts if review finds issues.
         * Each attempt regenerates code with feedback from the previous review.
         * Default: 2
         */
        private int maxRetryAttempts;
    }

    /**
     * Configuration for the Documentation Agent.
     *
     * <p>This agent generates documentation for generated code by analyzing
     * the code and finding related examples in the codebase.
     */
    @Data
    public static class DocumentationConfig {

        /**
         * Maximum number of code examples to retrieve from the codebase.
         * Used to provide context for documentation generation.
         * Default: 10
         */
        private int maxCodeMatches;

        /**
         * Maximum number of fallback documentation nodes to include.
         * Used when primary code matches are insufficient.
         * Default: 20
         */
        private int maxFallbackNodes;

        /**
         * Maximum number of log entries/preview lines to include in documentation.
         * Useful for showing example output or error scenarios.
         * Default: 5
         */
        private int maxLogPreview;
    }

    /**
     * Configuration for the Test Runner Agent.
     *
     * <p>This agent executes tests to validate generated code behavior.
     * It manages OS-specific shell invocation and test command execution.
     */
    @Data
    public static class TestRunnerConfig {

        /**
         * Shell executable to use on Windows systems.
         * Typically "cmd.exe" for native Windows command prompt.
         * Default: "cmd.exe"
         */
        private String shellWindows;

        /**
         * Shell flag for executing commands on Windows.
         * Used with shellWindows to specify command mode.
         * For cmd.exe, "/c" means execute the following command.
         * Default: "/c"
         */
        private String shellFlagWindows;

        /**
         * Shell executable to use on Unix-like systems (Linux, macOS).
         * Typically "sh" for POSIX shell.
         * Default: "sh"
         */
        private String shellUnix;

        /**
         * Shell flag for executing commands on Unix.
         * Used with shellUnix to specify command mode.
         * For sh, "-c" means execute the following command.
         * Default: "-c"
         */
        private String shellFlagUnix;

        /**
         * Test command to execute.
         * Should be the complete command as it would be typed in the shell.
         * Example: "mvn -B test" for Maven test execution with non-interactive mode.
         * Default: "mvn -B test"
         */
        private String testCommand;

        /**
         * Maximum time in minutes to wait for test execution.
         * Prevents tests from running indefinitely.
         * Default: 30
         */
        private int timeoutMinutes;
    }
}
