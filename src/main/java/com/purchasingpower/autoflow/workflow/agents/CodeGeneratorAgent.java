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

@Slf4j
@Component
@RequiredArgsConstructor
public class CodeGeneratorAgent {

    private final GeminiClient geminiClient;
    private final PromptLibraryService promptLibrary;
    private final ObjectMapper objectMapper;

    public Map<String, Object> execute(WorkflowState state) {
        log.info("💻 Generating code for {} files...", state.getScopeProposal().getTotalFileCount());

        try {
            CodeGenerationResponse code = generateCodeWithLLM(state);
            
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("generatedCode", code);

            log.info("✅ Code generated. Edits: {}, Tests: {}",
                    code.getEdits() != null ? code.getEdits().size() : 0,
                    code.getTestsAdded() != null ? code.getTestsAdded().size() : 0);

            updates.put("lastAgentDecision", AgentDecision.proceed("Code generation complete"));
            return updates;

        } catch (Exception e) {
            log.error("Failed to generate code", e);
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("lastAgentDecision", AgentDecision.error("Code generation failed: " + e.getMessage()));
            return updates;
        }
    }

    private CodeGenerationResponse generateCodeWithLLM(WorkflowState state) {
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

        Map<String, Object> variables = new HashMap<>();
        variables.put("requirement", state.getRequirement());
        variables.put("fileContexts", fileContextsData.values());
        variables.put("domainContext", Map.of(
                "domain", state.getContext().getDomainContext().getDomain(),
                "businessRules", state.getContext().getDomainContext().getBusinessRules(),
                "architecturePattern", state.getContext().getDomainContext().getArchitecturePattern()
        ));
        
        if (state.getLogAnalysis() != null) {
            variables.put("logAnalysis", Map.of(
                    "errorType", state.getLogAnalysis().getErrorType(),
                    "location", state.getLogAnalysis().getLocation(),
                    "rootCauseHypothesis", state.getLogAnalysis().getRootCauseHypothesis()
            ));
        }

        String prompt = promptLibrary.render("code-generator", variables);

        try {
            String jsonResponse = geminiClient.generateText(prompt);
            return objectMapper.readValue(jsonResponse, CodeGenerationResponse.class);
        } catch (Exception e) {
            log.error("Failed to generate code with LLM", e);
            throw new RuntimeException("Code generation failed", e);
        }
    }
}
