package com.purchasingpower.autoflow.workflow.steps;

import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.exception.BuildFailureException;
import com.purchasingpower.autoflow.model.llm.CodeGenerationResponse;
import com.purchasingpower.autoflow.service.FilePatchService;
import com.purchasingpower.autoflow.service.MavenBuildService;
import com.purchasingpower.autoflow.workflow.pipeline.PipelineContext;
import com.purchasingpower.autoflow.workflow.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class TestValidationStep implements PipelineStep {

    private final MavenBuildService buildService;
    private final GeminiClient geminiClient;
    private final FilePatchService patchService;

    private static final int MAX_RETRIES = 3;

    @Override
    public void execute(PipelineContext context) {
        log.info("Step 5: Build & Verification (Self-Healing Enabled)");

        int attempt = 1;
        boolean success = false;

        while (attempt <= MAX_RETRIES && !success) {
            try {
                log.info("Build Attempt {}/{}", attempt, MAX_RETRIES);
                buildService.buildAndVerify(context.getWorkspaceDir());
                success = true; // If we get here, no exception was thrown
                log.info("✅ Build Success!");

            } catch (BuildFailureException e) {
                log.error("❌ Build Attempt {} failed.", attempt);

                if (attempt == MAX_RETRIES) {
                    throw new RuntimeException("Build failed after " + MAX_RETRIES + " attempts. Pipeline Aborted.");
                }

                log.info("🩹 Engaging AI Auto-Fixer...");

                try {
                    // 1. Ask Gemini to fix the error logs
                    // We trim logs to last 4000 chars to fit context if they are huge
                    String logs = e.getErrorLogs();
                    if(logs.length() > 10000) logs = logs.substring(logs.length() - 10000);

                    CodeGenerationResponse fixPlan = geminiClient.generateFix(logs, context.getRequirements());

                    // 2. Apply the Fixes
                    patchService.applyChanges(context.getWorkspaceDir(), fixPlan);
                    log.info("Auto-Fix applied. Retrying build...");

                } catch (Exception aiEx) {
                    log.error("AI Fixer failed execution", aiEx);
                    throw new RuntimeException("AI Fixer failed, cannot recover.");
                }
            }
            attempt++;
        }
    }
}