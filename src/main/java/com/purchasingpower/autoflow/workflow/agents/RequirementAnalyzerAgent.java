package com.purchasingpower.autoflow.workflow.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.service.PromptLibraryService;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AGENT 1: Requirement Analyzer
 *
 * Purpose: Understand what the developer wants
 *
 * Key validations:
 * - Is requirement clear?
 * - Can we extract domain?
 * - Do we need clarification?
 *
 * Decision: PROCEED or ASK_DEV
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequirementAnalyzerAgent {

    private final GeminiClient geminiClient;
    private final PromptLibraryService promptLibrary;
    private final ObjectMapper objectMapper;

    public AgentDecision execute(WorkflowState state) {
        log.info("🔍 Analyzing requirement: {}", state.getRequirement());

        // Parse requirement with LLM (using prompt library!)
        RequirementAnalysis analysis = analyzeWithLLM(state);
        state.setRequirementAnalysis(analysis);

        // Check confidence
        if (analysis.getConfidence() < 0.7) {
            return AgentDecision.askDev(
                    "⚠️ **Unclear Requirement**\n\n" +
                            "I'm not confident I understand (confidence: " +
                            String.format("%.0f%%", analysis.getConfidence() * 100) + ").\n\n" +
                            "**Questions:**\n" +
                            String.join("\n", analysis.getQuestions())
            );
        }

        // Check if we have questions
        if (!analysis.getQuestions().isEmpty()) {
            return AgentDecision.askDev(
                    "📋 **Need Clarification**\n\n" +
                            String.join("\n", analysis.getQuestions())
            );
        }

        log.info("✅ Requirement analyzed. Type: {}, Domain: {}, Confidence: {}",
                analysis.getTaskType(),
                analysis.getDomain(),
                analysis.getConfidence());

        return AgentDecision.proceed("Requirement clear, proceeding to code indexing");
    }

    private RequirementAnalysis analyzeWithLLM(WorkflowState state) {
        // Render prompt from library
        String prompt = promptLibrary.render("requirement-analyzer", Map.of(
                "requirement", state.getRequirement(),
                "targetClass", state.getTargetClass() != null ? state.getTargetClass() : "",
                "hasLogs", state.hasLogs()
        ));

        try {
            // Call LLM
            String jsonResponse = geminiClient.generateText(prompt);

            // Parse JSON response
            return objectMapper.readValue(jsonResponse, RequirementAnalysis.class);

        } catch (Exception e) {
            log.error("Failed to analyze requirement with LLM", e);

            // Fallback: Simple analysis
            return RequirementAnalysis.builder()
                    .taskType("unknown")
                    .domain("unknown")
                    .summary(state.getRequirement())
                    .confidence(0.5)
                    .build();
        }
    }
}