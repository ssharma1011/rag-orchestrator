package com.purchasingpower.autoflow.workflow.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.model.llm.CodeGenerationResponse;
import com.purchasingpower.autoflow.service.PromptLibraryService;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AGENT 5: Code Generator
 *
 * Purpose: Generate code for ALL files in scope
 *
 * USES PROMPT LIBRARY (no hardcoded prompts!)
 * Inputs:
 * - Requirement
 * - Structured context (100% certain)
 * - Log analysis (if bug fix)
 * - Domain context from knowledge graph
 *
 * Output: CodeGenerationResponse with diffs
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeGeneratorAgent {

    private final GeminiClient geminiClient;
    private final PromptLibraryService promptLibrary;
    private final ObjectMapper objectMapper;

    public AgentDecision execute(WorkflowState state) {
        log.info("💻 Generating code for {} files...",
                state.getScopeProposal().getTotalFileCount());

        try {
            // Generate code using LLM (with prompt library!)
            CodeGenerationResponse code = generateCodeWithLLM(state);
            state.setGeneratedCode(code);

            log.info("✅ Code generated. Edits: {}, Tests: {}",
                    code.getEdits() != null ? code.getEdits().size() : 0,
                    code.getTestsAdded() != null ? code.getTestsAdded().size() : 0);

            return AgentDecision.proceed("Code generation complete");

        } catch (Exception e) {
            log.error("Failed to generate code", e);
            return AgentDecision.error("Code generation failed: " + e.getMessage());
        }
    }

    private CodeGenerationResponse generateCodeWithLLM(WorkflowState state) {
        // Prepare file contexts for template
        Map<String, Object> fileContextsData = state.getContext().getFileContexts().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Map.of(
                                "filePath", entry.getValue().getFilePath(),
                                "currentCode", entry.getValue().getCurrentCode(),
                                "purpose", entry.getValue().getPurpose(),
                                "dependencies", String.join(", ", entry.getValue().getDependencies())
                        )
                ));

        // Prepare variables for prompt template
        Map<String, Object> variables = new HashMap<>();
        variables.put("requirement", state.getRequirement());
        variables.put("fileContexts", fileContextsData.values());
        variables.put("domainContext", Map.of(
                "domain", state.getContext().getDomainContext().getDomain(),
                "businessRules", state.getContext().getDomainContext().getBusinessRules(),
                "architecturePattern", state.getContext().getDomainContext().getArchitecturePattern()
        ));

        // Add log analysis if available
        if (state.getLogAnalysis() != null) {
            variables.put("logAnalysis", Map.of(
                    "errorType", state.getLogAnalysis().getErrorType(),
                    "location", state.getLogAnalysis().getLocation(),
                    "rootCauseHypothesis", state.getLogAnalysis().getRootCauseHypothesis()
            ));
        }

        // Add review feedback if this is a retry
        if (state.getCodeReview() != null && !state.getCodeReview().isApproved()) {
            variables.put("reviewFeedback", Map.of(
                    "issues", state.getCodeReview().getIssues().stream()
                            .map(issue -> Map.of(
                                    "severity", issue.getSeverity().toString(),
                                    "description", issue.getDescription(),
                                    "suggestion", issue.getSuggestion()
                            ))
                            .toList()
            ));
        }

        // Render prompt using PROMPT LIBRARY
        String prompt = promptLibrary.render("code-generator", variables);

        try {
            // Call LLM
            String jsonResponse = geminiClient.generateText(prompt);

            // Parse JSON response
            return objectMapper.readValue(jsonResponse, CodeGenerationResponse.class);

        } catch (Exception e) {
            log.error("Failed to generate code with LLM", e);
            throw new RuntimeException("Code generation failed", e);
        }
    }
}