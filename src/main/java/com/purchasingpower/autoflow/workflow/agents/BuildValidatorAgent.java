package com.purchasingpower.autoflow.workflow.agents;

import com.purchasingpower.autoflow.model.llm.CodeGenerationResponse;
import com.purchasingpower.autoflow.service.FilePatchService;
import com.purchasingpower.autoflow.service.MavenBuildService;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * AGENT 6: Build Validator
 *
 * Purpose: Compile code and capture errors
 *
 * REUSES existing MavenBuildService!
 *
 * If build fails and attempt < 3:
 * - Return RETRY decision
 * - LangGraph4J will route back to CodeGeneratorAgent
 * - Generator receives build errors as feedback
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuildValidatorAgent {

    private final MavenBuildService buildService;
    private final FilePatchService patchService;

    private static final int MAX_RETRIES = 3;

    public AgentDecision execute(WorkflowState state) {
        log.info("🔨 Validating build (attempt {}/{})...",
                state.getBuildAttempt(), MAX_RETRIES);

        try {
            // Apply generated code to workspace
            applyCode(state);

            // Build with Maven (reuse existing service!)
            long startTime = System.currentTimeMillis();

            try {
                buildService.buildAndVerify(state.getWorkspaceDir());

                // Success!
                BuildResult result = BuildResult.builder()
                        .success(true)
                        .durationMs(System.currentTimeMillis() - startTime)
                        .build();

                state.setBuildResult(result);

                log.info("✅ Build successful in {}ms", result.getDurationMs());

                return AgentDecision.proceed("Build passed, proceeding to tests");

            } catch (com.purchasingpower.autoflow.exception.BuildFailureException e) {
                // Build failed
                BuildResult result = BuildResult.builder()
                        .success(false)
                        .buildLogs(e.getErrorLogs())
                        .compilationErrors(parseCompilationErrors(e.getErrorLogs()))
                        .durationMs(System.currentTimeMillis() - startTime)
                        .build();

                state.setBuildResult(result);
                state.incrementBuildAttempt();

                log.error("❌ Build failed (attempt {}): {}",
                        state.getBuildAttempt(),
                        result.getErrors());

                // Retry if attempts remaining
                if (state.getBuildAttempt() < MAX_RETRIES) {
                    return AgentDecision.retry(
                            "Build failed, retrying with error feedback"
                    );
                }

                // Max retries reached
                return AgentDecision.askDev(
                        "❌ **Build Failed After " + MAX_RETRIES + " Attempts**\n\n" +
                                "**Errors:**\n```\n" + result.getErrors() + "\n```\n\n" +
                                "Options:\n" +
                                "1. Retry with manual fixes\n" +
                                "2. Abort this task\n" +
                                "3. Show me the generated code"
                );
            }

        } catch (Exception e) {
            log.error("Build validation failed", e);
            return AgentDecision.error("Build validation error: " + e.getMessage());
        }
    }

    /**
     * Apply generated code to workspace (reuse existing FilePatchService!)
     */
    private void applyCode(WorkflowState state) throws Exception {
        CodeGenerationResponse code = state.getGeneratedCode();

        patchService.applyChanges(state.getWorkspaceDir(), code);

        log.info("Applied {} edits and {} tests to workspace",
                code.getEdits() != null ? code.getEdits().size() : 0,
                code.getTestsAdded() != null ? code.getTestsAdded().size() : 0);
    }

    /**
     * Parse Maven error logs into structured list
     */
    private java.util.List<String> parseCompilationErrors(String logs) {
        java.util.List<String> errors = new java.util.ArrayList<>();

        String[] lines = logs.split("\n");
        for (String line : lines) {
            if (line.contains("[ERROR]") || line.contains("error:")) {
                errors.add(line.trim());
            }
        }

        return errors;
    }
}