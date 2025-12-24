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
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PRReviewerAgent {

    private static final int MAX_RETRIES = 3;
    
    private final GeminiClient geminiClient;
    private final PromptLibraryService promptLibrary;
    private final ObjectMapper objectMapper;

    public Map<String, Object> execute(WorkflowState state) {
        log.info("👀 Reviewing PR quality (attempt {}/{})...", state.getReviewAttempt(), MAX_RETRIES);

        try {
            CodeReview review = reviewWithLLM(state);
            
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("codeReview", review);

            log.info("✅ Review complete. Approved: {}, Quality: {}, Issues: {}",
                    review.isApproved(), review.getQualityScore(), review.getIssues().size());

            if (review.isApproved()) {
                updates.put("lastAgentDecision", AgentDecision.proceed("Code review passed, creating PR"));
                return updates;
            }

            long criticalCount = review.getCriticalIssueCount();
            if (criticalCount > 0 && state.getReviewAttempt() < MAX_RETRIES) {
                int newAttempt = state.getReviewAttempt() + 1;
                updates.put("reviewAttempt", newAttempt);
                updates.put("lastAgentDecision", AgentDecision.retry("Critical issues found, regenerating code"));
                return updates;
            }

            updates.put("lastAgentDecision", AgentDecision.askDev(
                "⚠️ **Code Review Issues Found**\n\n" +
                review.getSummaryForDev() + "\n\n" +
                formatIssues(review) + "\n\n" +
                "Options:\n1. Proceed anyway\n2. Fix issues\n3. Abort"
            ));
            return updates;

        } catch (Exception e) {
            log.error("PR review failed", e);
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("lastAgentDecision", AgentDecision.error("PR review error: " + e.getMessage()));
            return updates;
        }
    }

    private CodeReview reviewWithLLM(WorkflowState state) {
        // FIX: Use edit.getPath() and edit.getOp() (not getFilePath/getChangeType)
        String edits = state.getGeneratedCode().getEdits().stream()
                .map(e -> e.getPath() + ": " + e.getOp())
                .collect(Collectors.joining("\n"));

        Map<String, Object> variables = new HashMap<>();
        variables.put("requirement", state.getRequirement());
        variables.put("edits", edits);
        variables.put("buildPassed", state.getBuildResult().isSuccess());
        variables.put("testsPassed", state.getTestResult().isAllTestsPassed());  // FIX: Use isAllTestsPassed()

        String prompt = promptLibrary.render("pr-reviewer", variables);

        try {
            String jsonResponse = geminiClient.generateText(prompt);
            return objectMapper.readValue(jsonResponse, CodeReview.class);
        } catch (Exception e) {
            log.error("Failed to review with LLM", e);
            return CodeReview.builder()
                    .approved(false)
                    .qualityScore(50)
                    .summaryForDev("Could not complete automated review")
                    .build();
        }
    }

    private String formatIssues(CodeReview review) {
        return review.getIssues().stream()
                .map(i -> "- [" + i.getSeverity() + "] " + i.getDescription())
                .collect(Collectors.joining("\n"));
    }
}
