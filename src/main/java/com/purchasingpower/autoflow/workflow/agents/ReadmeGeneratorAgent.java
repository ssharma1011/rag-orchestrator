package com.purchasingpower.autoflow.workflow.agents;

import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.service.PromptLibraryService;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadmeGeneratorAgent {

    private final GeminiClient geminiClient;
    private final PromptLibraryService promptLibrary;

    public Map<String, Object> execute(WorkflowState state) {
        log.info("📝 Generating PR description...");

        try {
            String prDescription = generateDescription(state);
            
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("prDescription", prDescription);

            log.info("✅ PR description generated ({} characters)", prDescription.length());
            
            updates.put("lastAgentDecision", AgentDecision.proceed("PR description ready"));
            return updates;

        } catch (Exception e) {
            log.error("Failed to generate PR description", e);
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("lastAgentDecision", AgentDecision.error("PR description generation failed: " + e.getMessage()));
            return updates;
        }
    }

    private String generateDescription(WorkflowState state) {
        // FIX: Use edit.getPath() and edit.getOp()
        String filesChanged = state.getGeneratedCode().getEdits().stream()
                .map(e -> "- " + e.getPath() + " (" + e.getOp() + ")")
                .collect(Collectors.joining("\n"));

        Map<String, Object> variables = new HashMap<>();
        variables.put("requirement", state.getRequirement());
        variables.put("taskType", state.getRequirementAnalysis().getTaskType());
        variables.put("filesChanged", filesChanged);
        variables.put("buildPassed", state.getBuildResult().isSuccess());
        variables.put("testsPassed", state.getTestResult().isAllTestsPassed());  // FIX
        
        if (state.getCodeReview() != null) {
            variables.put("qualityScore", state.getCodeReview().getQualityScore());
        }

        String prompt = promptLibrary.render("readme-generator", variables);
        return geminiClient.generateText(prompt);
    }
}
