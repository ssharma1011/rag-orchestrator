package com.purchasingpower.autoflow.workflow.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.service.PromptLibraryService;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequirementAnalyzerAgent {

    private final GeminiClient geminiClient;
    private final PromptLibraryService promptLibrary;
    private final ObjectMapper objectMapper;

    public Map<String, Object> execute(WorkflowState state) {
        log.info("🔍 Analyzing requirement: {}", state.getRequirement());

        try {
            RequirementAnalysis analysis = analyzeWithLLM(state);

            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("requirementAnalysis", analysis);

            if (analysis.getConfidence() < 0.7) {
                updates.put("lastAgentDecision", AgentDecision.askDev(
                        "⚠️ **Unclear Requirement**\n\n" +
                                "Confidence: " + String.format("%.0f%%", analysis.getConfidence() * 100) + "\n\n" +
                                "**Questions:**\n" + String.join("\n", analysis.getQuestions())
                ));
                return updates;
            }

            if (!analysis.getQuestions().isEmpty()) {
                updates.put("lastAgentDecision", AgentDecision.askDev(
                        "📋 **Need Clarification**\n\n" + String.join("\n", analysis.getQuestions())
                ));
                return updates;
            }

            log.info("✅ Requirement analyzed. Type: {}, Domain: {}, Confidence: {}",
                    analysis.getTaskType(), analysis.getDomain(), analysis.getConfidence());

            updates.put("lastAgentDecision", AgentDecision.proceed("Requirement clear, proceeding to code indexing"));
            return updates;

        } catch (Exception e) {
            log.error("Requirement analysis failed", e);
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("lastAgentDecision", AgentDecision.error(e.getMessage()));
            return updates;
        }
    }

    private RequirementAnalysis analyzeWithLLM(WorkflowState state) {
        String prompt = promptLibrary.render("requirement-analyzer", Map.of(
                "requirement", state.getRequirement(),
                "targetClass", state.getTargetClass() != null ? state.getTargetClass() : "",
                "hasLogs", state.hasLogs()
        ));

        try {
            String jsonResponse = geminiClient.generateText(prompt);

            String cleanJson = getTrimmedString(jsonResponse);

            return objectMapper.readValue(cleanJson, RequirementAnalysis.class);
        } catch (Exception e) {
            log.error("Failed to analyze requirement with LLM", e);
            return RequirementAnalysis.builder()
                    .taskType("unknown")
                    .domain("unknown")
                    .summary(state.getRequirement())
                    .confidence(0.5)
                    .build();
        }
    }

    private static String getTrimmedString(String jsonResponse) {
        String cleanJson = jsonResponse.trim();
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.substring(7); // Remove ```json
        }
        if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.substring(3); // Remove ```
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.substring(0, cleanJson.length() - 3); // Remove trailing ```
        }
        cleanJson = cleanJson.trim();
        return cleanJson;
    }
}