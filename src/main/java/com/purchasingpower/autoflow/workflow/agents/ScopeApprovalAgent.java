package com.purchasingpower.autoflow.workflow.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.model.agent.ApprovalResult;
import com.purchasingpower.autoflow.service.PromptLibraryService;
import com.purchasingpower.autoflow.workflow.state.AgentDecision;
import com.purchasingpower.autoflow.workflow.state.ChatMessage;
import com.purchasingpower.autoflow.workflow.state.ScopeProposal;
import com.purchasingpower.autoflow.workflow.state.WorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * ScopeApprovalAgent - LLM-based natural language approval detection.
 *
 * <p>WHY LLM instead of keywords:
 * Developers type naturally - "that looks right", "perfect, let's do it",
 * "yeah but change X to Y". Hardcoded keywords can't handle the variety
 * and nuance of human language.
 *
 * <p>This agent uses the scope-approval.yaml prompt to send user responses
 * to Gemini for intelligent interpretation, returning structured JSON:
 * <pre>
 * {
 *   "approved": true|false,
 *   "approvalType": "full|partial|rejected",
 *   "modifiedScope": {...}  // if partial approval with changes
 * }
 * </pre>
 *
 * @see PromptLibraryService
 * @see GeminiClient
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScopeApprovalAgent {

    private final GeminiClient geminiClient;
    private final PromptLibraryService promptLibraryService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> execute(WorkflowState state) {
        log.info("✅ Validating scope approval using LLM natural language understanding...");

        Map<String, Object> updates = new HashMap<>(state.toMap());

        // Check if there's a scope proposal to approve
        ScopeProposal proposal = state.getScopeProposal();
        if (proposal == null) {
            log.warn("⚠️ No scope proposal found in state - skipping approval validation");
            updates.put("lastAgentDecision", AgentDecision.proceed("No scope proposal to validate"));
            return updates;
        }

        // Get user's latest response
        List<ChatMessage> history = state.getConversationHistory();
        if (history == null || history.isEmpty()) {
            log.warn("⚠️ No conversation history - cannot validate approval");
            updates.put("lastAgentDecision", AgentDecision.askDev(
                "Please respond to approve or modify the scope."
            ));
            return updates;
        }

        // Get last user message
        String userResponse = history.stream()
                .filter(m -> "user".equals(m.getRole()))
                .reduce((first, second) -> second)  // Get last
                .map(ChatMessage::getContent)
                .orElse("");

        if (userResponse.isBlank()) {
            log.warn("⚠️ Empty user response");
            updates.put("lastAgentDecision", AgentDecision.askDev(
                "Please respond to approve or modify the scope."
            ));
            return updates;
        }

        log.info("📨 User response: '{}'", userResponse);

        // Use LLM to interpret natural language response
        try {
            ApprovalResult result = interpretResponseWithLLM(
                userResponse,
                proposal,
                state.getConversationId()
            );
            log.info("🎯 LLM interpretation: approved={}, type={}",
                    result.isApproved(), result.getApprovalType());

            if (result.isApproved() && "full".equals(result.getApprovalType())) {
                log.info("✅ Full approval - proceeding to context builder");
                updates.put("lastAgentDecision", AgentDecision.proceed("User approved scope fully"));

            } else if (result.isApproved() && "partial".equals(result.getApprovalType())) {
                log.info("🔧 Partial approval with modifications requested");
                updates.put("lastAgentDecision", AgentDecision.askDev(
                    "🔧 **Modifications Requested**\n\n" +
                    "I understand you want to make some changes. " +
                    "Please specify exactly what should be modified:\n" +
                    "- Which files to add or remove?\n" +
                    "- What specific changes are needed?\n\n" +
                    "Or say 'proceed as-is' to continue with current scope."
                ));

            } else {
                log.info("❌ Rejection detected - asking for clarification");
                updates.put("lastAgentDecision", AgentDecision.askDev(
                    "❌ **Scope Needs Changes**\n\n" +
                    "I understand the current scope doesn't match your needs. " +
                    "Please clarify:\n" +
                    "- What should be changed?\n" +
                    "- Which files/components are incorrect?\n" +
                    "- What's the correct scope?\n\n" +
                    "Or provide a new requirement description to start over."
                ));
            }

        } catch (Exception e) {
            log.error("❌ Failed to interpret user response with LLM", e);
            // Fallback: ask for clarification
            updates.put("lastAgentDecision", AgentDecision.askDev(
                "❓ **Response Unclear**\n\n" +
                "I had trouble understanding your response: \"" + userResponse + "\"\n\n" +
                "Please respond clearly:\n" +
                "- Say **'yes'**, **'approved'**, or **'looks good'** to proceed\n" +
                "- Say **'no'** or **'reject'** to cancel\n" +
                "- Describe specific changes if you want modifications"
            ));
        }

        return updates;
    }

    /**
     * Use LLM to interpret user response with natural language understanding.
     *
     * <p>Sends user response to Gemini with scope-approval.yaml prompt.
     * LLM returns structured JSON indicating approval status.
     *
     * @param userResponse user's natural language response
     * @param proposal the scope proposal being evaluated
     * @param conversationId conversation ID for LLM context
     * @return structured approval result
     * @throws Exception if LLM call fails or response is invalid
     */
    private ApprovalResult interpretResponseWithLLM(String userResponse, ScopeProposal proposal, String conversationId) throws Exception {
        // Build prompt context
        Map<String, Object> context = new HashMap<>();
        context.put("userResponse", userResponse);
        context.put("proposedScope", formatProposal(proposal));

        // Load and execute scope-approval.yaml prompt
        String promptText = promptLibraryService.render("scope-approval", context);

        // Call Gemini to get approval interpretation
        String jsonResponse = geminiClient.callChatApi(
            promptText,
            "scope-approval",
            conversationId
        );

        // Parse JSON response
        JsonNode root = objectMapper.readTree(jsonResponse);

        ApprovalResult.ApprovalResultBuilder builder = ApprovalResult.builder()
                .approved(root.path("approved").asBoolean(false))
                .approvalType(root.path("approvalType").asText("rejected"))
                .confidence(root.path("confidence").asDouble(0.0));

        if (root.has("modifiedScope")) {
            builder.modifiedScope(objectMapper.convertValue(
                root.get("modifiedScope"),
                Map.class
            ));
        }

        ApprovalResult result = builder.build();

        log.info("🤖 LLM confidence: {}", String.format("%.0f%%", result.getConfidence() * 100));

        return result;
    }

    /**
     * Format scope proposal for LLM context.
     */
    private String formatProposal(ScopeProposal proposal) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Files to modify:**\n");
        if (proposal.getFilesToModify() != null) {
            proposal.getFilesToModify().forEach(f ->
                sb.append("- ").append(f).append("\n")
            );
        }
        sb.append("\n**Files to create:**\n");
        if (proposal.getFilesToCreate() != null) {
            proposal.getFilesToCreate().forEach(f ->
                sb.append("- ").append(f).append("\n")
            );
        }
        sb.append("\n**Tests to update:**\n");
        if (proposal.getTestsToUpdate() != null) {
            proposal.getTestsToUpdate().forEach(t ->
                sb.append("- ").append(t).append("\n")
            );
        }
        return sb.toString();
    }
}
