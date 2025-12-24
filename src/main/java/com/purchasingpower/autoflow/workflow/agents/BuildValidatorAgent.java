package com.purchasingpower.autoflow.workflow.agents;

import com.purchasingpower.autoflow.service.MavenBuildService;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BuildValidatorAgent {

    private static final int MAX_RETRIES = 3;
    
    private final MavenBuildService mavenBuildService;  // FIX: Correct service name

    public Map<String, Object> execute(WorkflowState state) {
        log.info("🔨 Validating build (attempt {}/{})...", state.getBuildAttempt(), MAX_RETRIES);

        try {
            // Apply code changes to workspace
            applyCode(state);
            
            // Run build
            BuildResult result = mavenBuildService.buildAndVerify(state.getWorkspaceDir());
            
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("buildResult", result);

            if (result.isSuccess()) {
                log.info("✅ Build passed!");
                updates.put("lastAgentDecision", AgentDecision.proceed("Build successful"));
                return updates;
            }

            log.warn("❌ Build failed: {}", result.getErrors());
            
            if (state.getBuildAttempt() < MAX_RETRIES) {
                int newAttempt = state.getBuildAttempt() + 1;
                updates.put("buildAttempt", newAttempt);
                updates.put("lastAgentDecision", AgentDecision.retry("Build failed, regenerating code"));
                return updates;
            }

            updates.put("lastAgentDecision", AgentDecision.askDev(
                "⚠️ **Build Failed After " + MAX_RETRIES + " Attempts**\n\n" +
                "Error: " + result.getErrors() + "\n\n" +
                "Options:\n1. Skip build validation\n2. Fix manually\n3. Abort"
            ));
            return updates;

        } catch (Exception e) {
            log.error("Build validation failed", e);
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("lastAgentDecision", AgentDecision.error("Build validation error: " + e.getMessage()));
            return updates;
        }
    }

    private void applyCode(WorkflowState state) throws Exception {
        // Apply file edits
        for (com.purchasingpower.autoflow.model.llm.FileEdit edit : state.getGeneratedCode().getEdits()) {
            File file = new File(state.getWorkspaceDir(), edit.getPath());
            
            if ("create".equals(edit.getOp()) || "modify".equals(edit.getOp())) {
                file.getParentFile().mkdirs();
                Files.writeString(file.toPath(), edit.getContent());
            } else if ("delete".equals(edit.getOp())) {
                file.delete();
            }
        }
    }
}
