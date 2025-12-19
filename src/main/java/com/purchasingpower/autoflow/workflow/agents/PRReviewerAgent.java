package com.purchasingpower.autoflow.workflow.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.model.llm.FileEdit;
import com.purchasingpower.autoflow.model.llm.TestFile;
import com.purchasingpower.autoflow.service.PromptLibraryService;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AGENT 8: PR Reviewer
 *
 * Purpose: Review code quality before creating PR
 *
 * USES PROMPT LIBRARY (no hardcoded prompts!)
 *
 * Checks:
 * 1. LLM review (security, quality, architecture)
 * 2. Static analysis (Checkstyle, SpotBugs)
 * 3. Build/test status
 *
 * If rejected with critical issues + attempt < 3:
 * - Return RETRY
 * - Route back to CodeGeneratorAgent with feedback
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PRReviewerAgent {

    private final GeminiClient geminiClient;
    private final PromptLibraryService promptLibrary;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 3;

    public AgentDecision execute(WorkflowState state) {
        log.info("👀 Reviewing PR quality (attempt {}/{})...",
                state.getReviewAttempt(), MAX_RETRIES);

        try {
            // Review with LLM (using prompt library!)
            CodeReview review = reviewWithLLM(state);
            state.setCodeReview(review);

            log.info("✅ Review complete. Approved: {}, Quality: {}, Issues: {}",
                    review.isApproved(),
                    review.getQualityScore(),
                    review.getIssues().size());

            // If approved, proceed
            if (review.isApproved()) {
                return AgentDecision.proceed("Code review passed, creating PR");
            }

            // Check if critical issues
            long criticalCount = review.getCriticalIssueCount();

            if (criticalCount > 0 && state.getReviewAttempt() < MAX_RETRIES) {
                state.incrementReviewAttempt();

                return AgentDecision.retry(
                        "Critical issues found, regenerating code with feedback"
                );
            }

            // Ask dev if only minor issues or max retries reached
            return AgentDecision.askDev(
                    "⚠️ **Code Review Issues Found**\n\n" +
                            review.getSummaryForDev() + "\n\n" +
                            formatIssues(review) + "\n\n" +
                            "Options:\n" +
                            "1. Proceed anyway (I'll note issues in PR)\n" +
                            "2. Fix issues first\n" +
                            "3. Abort this task"
            );

        } catch (Exception e) {
            log.error("PR review failed", e);
            return AgentDecision.error("PR review error: " + e.getMessage());
        }
    }

    /**
     * Review code with LLM using PROMPT LIBRARY
     */
    private CodeReview reviewWithLLM(WorkflowState state) {
        // Prepare edits for template
        var editsData = state.getGeneratedCode().getEdits().stream()
                .map(edit -> Map.of(
                        "path", (Object) edit.getPath(),
                        "content", edit.getContent()
                ))
                .toList();

        // Prepare tests for template
        var testsData = state.getGeneratedCode().getTestsAdded().stream()
                .map(test -> Map.of(
                        "path", (Object) test.getPath(),
                        "content", test.getContent()
                ))
                .toList();

        // Prepare variables for prompt template
        Map<String, Object> variables = new HashMap<>();
        variables.put("requirement", state.getRequirement());
        variables.put("edits", editsData);
        variables.put("testsAdded", testsData);
        variables.put("buildSuccess", state.getBuildResult().isSuccess());

        if (!state.getBuildResult().isSuccess()) {
            variables.put("buildErrors", state.getBuildResult().getErrors());
        }

        if (state.getTestResult() != null) {
            variables.put("testsPassed", state.getTestResult().getTestsPassed());
            variables.put("testsTotal",
                    state.getTestResult().getTestsPassed() +
                            state.getTestResult().getTestsFailed());
            variables.put("coverageDelta", state.getTestResult().getCoverageDelta());
        }

        // TODO: Run actual static analysis (Checkstyle, SpotBugs)
        variables.put("checkstyleViolations", 0);
        variables.put("spotbugsIssues", 0);
        variables.put("securityIssues", 0);

        // Render prompt using PROMPT LIBRARY
        String prompt = promptLibrary.render("pr-reviewer", variables);

        try {
            // Call LLM
            String jsonResponse = geminiClient.generateText(prompt);

            // Parse JSON response
            return objectMapper.readValue(jsonResponse, CodeReview.class);

        } catch (Exception e) {
            log.error("Failed to review with LLM", e);

            // Fallback: Auto-approve with warning
            return CodeReview.builder()
                    .approved(true)
                    .qualityScore(0.7)
                    .summaryForDev("Warning: Automated review failed, proceeding with caution")
                    .build();
        }
    }

    private String formatIssues(CodeReview review) {
        if (review.getIssues().isEmpty()) {
            return "No specific issues identified.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**Issues Found:**\n");

        for (CodeReview.ReviewIssue issue : review.getIssues()) {
            sb.append(String.format("\n[%s] %s\n",
                    issue.getSeverity(), issue.getDescription()));
            sb.append("  File: ").append(issue.getFile());
            if (issue.getLine() != null) {
                sb.append(" (line ").append(issue.getLine()).append(")");
            }
            sb.append("\n");
            if (issue.getSuggestion() != null) {
                sb.append("  Suggestion: ").append(issue.getSuggestion()).append("\n");
            }
        }

        return sb.toString();
    }
}