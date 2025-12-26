package com.purchasingpower.autoflow.workflow.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.service.PromptLibraryService;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
            RequirementAnalysis existingAnalysis = state.getRequirementAnalysis();
            List<ChatMessage> conversationHistory = state.getConversationHistory();

            boolean userJustResponded = conversationHistory != null &&
                    conversationHistory.size() > 1 &&
                    conversationHistory.get(conversationHistory.size() - 1)
                            .getRole().equals("user");

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

            // NEW: Check if this is a repeat question (conversation history exists)
            if (!analysis.getQuestions().isEmpty() &&
                    conversationHistory != null &&
                    !conversationHistory.isEmpty()) {

                log.warn("⚠️ LLM asked questions despite having conversation history!");
                log.warn("Questions: {}", analysis.getQuestions());

                // Check if questions were already answered
                String lastUserMessage = conversationHistory.stream()
                        .filter(msg -> "user".equals(msg.getRole()))
                        .reduce((first, second) -> second)  // Get last
                        .map(ChatMessage::getContent)
                        .orElse("");

                if (!lastUserMessage.isEmpty()) {
                    log.info("✅ User already responded. Proceeding without asking again.");
                    analysis.setQuestions(new ArrayList<>());  // Clear questions
                }
            }

            // Skip questions for documentation tasks
            boolean isDocumentationTask = "documentation".equalsIgnoreCase(analysis.getTaskType());

            // Check confidence
            if (analysis.getConfidence() < 0.7) {
                log.warn("⚠️ Low confidence ({}) - requesting clarification",
                        String.format("%.0f%%", analysis.getConfidence() * 100));

                String clarificationMessage = "⚠️ **Unclear Requirement**\n\n" +
                        "Confidence: " + String.format("%.0f%%", analysis.getConfidence() * 100) + "\n\n" +
                        "**Questions:**\n" + String.join("\n", analysis.getQuestions());

                // CRITICAL FIX: Add assistant's clarification request to conversation history
                List<ChatMessage> updatedHistory = new ArrayList<>(
                        conversationHistory != null ? conversationHistory : new ArrayList<>());

                ChatMessage assistantMessage = new ChatMessage();
                assistantMessage.setRole("assistant");
                assistantMessage.setContent(clarificationMessage);
                assistantMessage.setTimestamp(java.time.LocalDateTime.now());
                updatedHistory.add(assistantMessage);

                updates.put("conversationHistory", updatedHistory);
                updates.put("lastAgentDecision", AgentDecision.askDev(clarificationMessage));

                log.debug("✅ Added clarification request to conversation history");
                return updates;
            }

            // Check for clarifying questions (only if not documentation task)
            if (!isDocumentationTask && !analysis.getQuestions().isEmpty()) {
                log.info("📋 Need clarification - {} questions", analysis.getQuestions().size());

                String questionsMessage = "📋 **Need Clarification**\n\n" +
                        "Please answer ALL questions below:\n\n" +
                        String.join("\n", analysis.getQuestions());

                // CRITICAL FIX: Add assistant's questions to conversation history
                // so when user responds, LLM knows what questions it asked
                List<ChatMessage> updatedHistory = new ArrayList<>(
                        conversationHistory != null ? conversationHistory : new ArrayList<>());

                ChatMessage assistantMessage = new ChatMessage();
                assistantMessage.setRole("assistant");
                assistantMessage.setContent(questionsMessage);
                assistantMessage.setTimestamp(java.time.LocalDateTime.now());
                updatedHistory.add(assistantMessage);

                updates.put("conversationHistory", updatedHistory);
                updates.put("lastAgentDecision", AgentDecision.askDev(questionsMessage));

                log.debug("✅ Added {} questions to conversation history", analysis.getQuestions().size());
                return updates;
            }

            // If documentation task had questions, clear them
            if (isDocumentationTask && !analysis.getQuestions().isEmpty()) {
                log.info("📚 Documentation task - clearing {} questions", analysis.getQuestions().size());
                analysis.setQuestions(new ArrayList<>());
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
        // Format conversation history for LLM
        String conversationHistory = formatConversationHistory(history);

        String prompt = promptLibrary.render("requirement-analyzer", Map.of(
                "requirement", state.getRequirement(),
                "targetClass", state.getTargetClass() != null ? state.getTargetClass() : "",
                "hasLogs", state.hasLogs(),
                "conversationHistory", conversationHistory
        ));

        String llmResponse = geminiClient.callChatApi(prompt, "requirement-analyzer");

        try {
            // Parse JSON response
            String cleanJson = llmResponse
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            RequirementAnalysis analysis = objectMapper.readValue(cleanJson, RequirementAnalysis.class);

            log.debug("📊 Requirement analysis: taskType='{}', domain='{}', confidence={}, questions={}",
                    analysis.getTaskType(), analysis.getDomain(), analysis.getConfidence(),
                    analysis.getQuestions().size());


            return analysis;

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

    /**
     * Format conversation history for LLM prompts.
     */
    private String formatConversationHistory(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "This is the first message in the conversation.";
        }

        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : history) {
            sb.append(String.format("[%s]: %s\n",
                    msg.getRole().toUpperCase(),
                    msg.getContent()));
        }
        return sb.toString();
    }
}
