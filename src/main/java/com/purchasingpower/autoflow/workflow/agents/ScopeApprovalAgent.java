package com.purchasingpower.autoflow.workflow.agents;

import com.purchasingpower.autoflow.workflow.state.AgentDecision;
import com.purchasingpower.autoflow.workflow.state.ChatMessage;
import com.purchasingpower.autoflow.workflow.state.ScopeProposal;
import com.purchasingpower.autoflow.workflow.state.WorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Validates user responses to scope proposals with fuzzy approval matching.
 *
 * Handles natural language responses like:
 * - "yes", "yeah", "yep", "yup"
 * - "ok", "okay", "k"
 * - "approved", "approve", "confirm"
 * - "go ahead", "proceed", "continue"
 * - "implement your plan", "looks good"
 * - "no", "nope", "reject", "cancel"
 * - "modify", "change", "adjust"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScopeApprovalAgent {

    // Fuzzy approval patterns (case-insensitive)
    private static final Set<String> APPROVAL_KEYWORDS = Set.of(
        "yes", "yeah", "yep", "yup", "ya",
        "ok", "okay", "k", "kk",
        "approved", "approve", "confirm", "confirmed",
        "proceed", "continue", "go", "ahead",
        "looks good", "lgtm", "sounds good",
        "do it", "implement", "ship", "ship it",
        "correct", "right", "exactly"
    );

    private static final Set<String> REJECTION_KEYWORDS = Set.of(
        "no", "nope", "nah",
        "reject", "rejected", "decline",
        "cancel", "cancelled", "stop",
        "wrong", "incorrect"
    );

    private static final Set<String> MODIFICATION_KEYWORDS = Set.of(
        "modify", "change", "adjust", "update",
        "edit", "revise", "different",
        "but", "except", "instead"
    );

    // Patterns for multi-word approval phrases
    private static final List<Pattern> APPROVAL_PATTERNS = List.of(
        Pattern.compile("go\\s+ahead", Pattern.CASE_INSENSITIVE),
        Pattern.compile("looks?\\s+good", Pattern.CASE_INSENSITIVE),
        Pattern.compile("sounds?\\s+good", Pattern.CASE_INSENSITIVE),
        Pattern.compile("implement.*plan", Pattern.CASE_INSENSITIVE),
        Pattern.compile("your\\s+plan", Pattern.CASE_INSENSITIVE),
        Pattern.compile("do\\s+it", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ship\\s+it", Pattern.CASE_INSENSITIVE)
    );

    public Map<String, Object> execute(WorkflowState state) {
        log.info("✅ Validating scope approval response...");

        Map<String, Object> updates = new HashMap<>(state.toMap());

        // Check if there's a scope proposal to approve
        ScopeProposal proposal = state.getScopeProposal();
        if (proposal == null) {
            log.warn("⚠️ No scope proposal found in state - skipping approval validation");
            updates.put("lastAgentDecision", AgentDecision.proceed());
            return updates;
        }

        // Get user's latest response
        List<ChatMessage> history = state.getConversationHistory();
        if (history == null || history.isEmpty()) {
            log.warn("⚠️ No conversation history - cannot validate approval");
            updates.put("lastAgentDecision", AgentDecision.askDev(
                "Please respond with 'yes' to approve or 'no' to reject the scope."
            ));
            return updates;
        }

        // Get last user message
        String userResponse = history.stream()
                .filter(m -> "user".equals(m.getRole()))
                .reduce((first, second) -> second)  // Get last
                .map(ChatMessage::getContent)
                .orElse("");

        log.info("📨 User response: '{}'", userResponse);

        // Validate response with fuzzy matching
        ApprovalDecision decision = parseApproval(userResponse);

        log.info("🎯 Approval decision: {}", decision);

        switch (decision) {
            case APPROVED:
                log.info("✅ Scope approved! Proceeding to context builder...");
                updates.put("lastAgentDecision", AgentDecision.proceed());
                break;

            case REJECTED:
                log.info("❌ Scope rejected. Asking for clarification...");
                updates.put("lastAgentDecision", AgentDecision.askDev(
                    "❌ **Scope Rejected**\n\n" +
                    "Please provide more details about what you'd like changed:\n" +
                    "- Which files should be added/removed?\n" +
                    "- What's the correct scope?\n" +
                    "- Any specific requirements I missed?"
                ));
                break;

            case MODIFICATION_REQUESTED:
                log.info("🔧 User wants modifications. Asking for details...");
                updates.put("lastAgentDecision", AgentDecision.askDev(
                    "🔧 **Modification Requested**\n\n" +
                    "I understand you want to make changes. Please specify:\n" +
                    "- What specifically should be changed?\n" +
                    "- Which files to add or remove?\n" +
                    "- Any other requirements?"
                ));
                break;

            case UNCLEAR:
                log.info("❓ Response unclear. Asking user to clarify...");
                updates.put("lastAgentDecision", AgentDecision.askDev(
                    "❓ **Response Unclear**\n\n" +
                    "I didn't understand your response: \"" + userResponse + "\"\n\n" +
                    "Please respond with:\n" +
                    "- **'yes'** or **'approved'** to proceed\n" +
                    "- **'no'** or **'reject'** to cancel\n" +
                    "- **'modify'** with details if you want changes"
                ));
                break;
        }

        return updates;
    }

    /**
     * Parse user response with fuzzy matching.
     */
    private ApprovalDecision parseApproval(String response) {
        if (response == null || response.isBlank()) {
            return ApprovalDecision.UNCLEAR;
        }

        String normalized = response.toLowerCase().trim();

        log.info("🔍 Analyzing response: '{}'", normalized);

        // Check exact keyword matches first
        for (String keyword : APPROVAL_KEYWORDS) {
            if (normalized.equals(keyword) || normalized.contains(" " + keyword + " ") ||
                normalized.startsWith(keyword + " ") || normalized.endsWith(" " + keyword)) {
                log.info("   ✅ Matched approval keyword: '{}'", keyword);
                return ApprovalDecision.APPROVED;
            }
        }

        // Check approval patterns
        for (Pattern pattern : APPROVAL_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                log.info("   ✅ Matched approval pattern: {}", pattern);
                return ApprovalDecision.APPROVED;
            }
        }

        // Check rejection keywords
        for (String keyword : REJECTION_KEYWORDS) {
            if (normalized.equals(keyword) || normalized.contains(" " + keyword + " ") ||
                normalized.startsWith(keyword + " ") || normalized.endsWith(" " + keyword)) {
                log.info("   ❌ Matched rejection keyword: '{}'", keyword);
                return ApprovalDecision.REJECTED;
            }
        }

        // Check modification keywords
        for (String keyword : MODIFICATION_KEYWORDS) {
            if (normalized.contains(keyword)) {
                log.info("   🔧 Matched modification keyword: '{}'", keyword);
                return ApprovalDecision.MODIFICATION_REQUESTED;
            }
        }

        // Default to unclear if no patterns matched
        log.info("   ❓ No clear approval/rejection pattern found");
        return ApprovalDecision.UNCLEAR;
    }

    private enum ApprovalDecision {
        APPROVED,
        REJECTED,
        MODIFICATION_REQUESTED,
        UNCLEAR
    }
}
