package com.purchasingpower.autoflow.workflow.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.service.PromptLibraryService;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FIXED: RequirementAnalyzerAgent no longer loops on clarifications.
 *
 * Logic:
 * 1. If already analyzed AND user provided response → PROCEED (don't re-analyze)
 * 2. If not analyzed yet → Analyze and ask questions if needed
 * 3. If low confidence → Ask for clarification ONCE
 */
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
            // CRITICAL FIX: Check if already analyzed
            RequirementAnalysis existingAnalysis = state.getRequirementAnalysis();
            List<ChatMessage> conversationHistory = state.getConversationHistory();

            boolean userJustResponded = conversationHistory != null &&
                    conversationHistory.size() > 1 &&
                    conversationHistory.get(conversationHistory.size() - 1)
                            .getRole().equals("user");

            // If already analyzed AND user just responded to our questions → PROCEED
            if (existingAnalysis != null && userJustResponded) {
                log.info("✅ User provided clarification. Proceeding with existing analysis.");
                log.info("   - Task Type: {}", existingAnalysis.getTaskType());
                log.info("   - Domain: {}", existingAnalysis.getDomain());
                log.info("   - Summary: {}", existingAnalysis.getSummary());

                Map<String, Object> updates = new HashMap<>(state.toMap());
                updates.put("lastAgentDecision", AgentDecision.proceed(
                        "Requirement clarified, proceeding to next step"
                ));
                return updates;
            }

            // First time analyzing OR confidence was too low before
            RequirementAnalysis analysis = analyzeWithLLM(state, conversationHistory);

            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("requirementAnalysis", analysis);

            // Check confidence
            if (analysis.getConfidence() < 0.7) {
                log.warn("⚠️ Low confidence ({}) - requesting clarification",
                        String.format("%.0f%%", analysis.getConfidence() * 100));

                updates.put("lastAgentDecision", AgentDecision.askDev(
                        "⚠️ **Unclear Requirement**\n\n" +
                                "Confidence: " + String.format("%.0f%%", analysis.getConfidence() * 100) + "\n\n" +
                                "**Questions:**\n" + String.join("\n", analysis.getQuestions())
                ));
                return updates;
            }

            // Check for clarifying questions
            if (!analysis.getQuestions().isEmpty()) {
                log.info("📋 Need clarification - {} questions", analysis.getQuestions().size());

                updates.put("lastAgentDecision", AgentDecision.askDev(
                        "📋 **Need Clarification**\n\n" + String.join("\n", analysis.getQuestions())
                ));
                return updates;
            }

            // All clear! Proceed
            log.info("✅ Requirement analyzed successfully");
            log.info("   - Type: {}", analysis.getTaskType());
            log.info("   - Domain: {}", analysis.getDomain());
            log.info("   - Confidence: {}", String.format("%.0f%%", analysis.getConfidence() * 100));

            updates.put("lastAgentDecision", AgentDecision.proceed(
                    "Requirement clear, proceeding to code indexing"
            ));
            return updates;

        } catch (Exception e) {
            log.error("❌ Requirement analysis failed", e);
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("lastAgentDecision", AgentDecision.error(e.getMessage()));
            return updates;
        }
    }

    /**
     * Analyze requirement with LLM, including conversation history for context.
     */
    private RequirementAnalysis analyzeWithLLM(WorkflowState state, List<ChatMessage> history) {
        // Build context from conversation history
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("**Requirement:** ").append(state.getRequirement()).append("\n\n");

        if (history != null && !history.isEmpty()) {
            contextBuilder.append("**Previous Conversation:**\n");
            for (ChatMessage msg : history) {
                contextBuilder.append(msg.getRole()).append(": ")
                        .append(msg.getContent()).append("\n");
            }
        }

        String prompt = promptLibrary.render("requirement-analyzer", Map.of(
                "requirement", state.getRequirement(),
                "targetClass", state.getTargetClass() != null ? state.getTargetClass() : "",
                "hasLogs", state.hasLogs(),
                "conversationContext", contextBuilder.toString()
        ));

        String llmResponse = geminiClient.callChatApi(prompt, "requirement-analyzer");

        try {
            // Parse JSON response
            String cleanJson = llmResponse
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            return objectMapper.readValue(cleanJson, RequirementAnalysis.class);

        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", llmResponse, e);

            // Fallback: create analysis from response text
            return RequirementAnalysis.builder()
                    .taskType("unknown")
                    .domain("general")
                    .summary(state.getRequirement())
                    .detailedDescription(llmResponse)
                    .confidence(0.5)
                    .build();
        }
    }
}
