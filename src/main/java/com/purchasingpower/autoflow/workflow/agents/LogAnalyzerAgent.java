package com.purchasingpower.autoflow.workflow.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.service.PromptLibraryService;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AGENT 2: Log Analyzer (Optional - runs if logs provided)
 *
 * Purpose: Parse logs to extract error details
 *
 * Runs AFTER RequirementAnalyzer, BEFORE CodeIndexer
 * Only if hasLogs() == true
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogAnalyzerAgent {

    private final GeminiClient geminiClient;
    private final PromptLibraryService promptLibrary;
    private final ObjectMapper objectMapper;

    public AgentDecision execute(WorkflowState state) {
        if (!state.hasLogs()) {
            log.info("⏭️ No logs provided, skipping log analysis");
            return AgentDecision.proceed("No logs to analyze");
        }

        log.info("📋 Analyzing logs...");

        try {
            // Read attached log files
            String logContent = readLogs(state);

            // Analyze with LLM (using prompt library!)
            LogAnalysis analysis = analyzeLogsWithLLM(state, logContent);
            state.setLogAnalysis(analysis);

            log.info("✅ Log analysis complete. Error: {}, Location: {}, Confidence: {}",
                    analysis.getErrorType(),
                    analysis.getLocation(),
                    analysis.getConfidence());

            // If low confidence, ask dev
            if (analysis.getConfidence() < 0.7) {
                return AgentDecision.askDev(
                        "⚠️ **Unclear Error from Logs**\n\n" +
                                "I found: " + analysis.getErrorType() + "\n" +
                                "Location: " + analysis.getLocation() + "\n" +
                                "Hypothesis: " + analysis.getRootCauseHypothesis() + "\n\n" +
                                "**Questions:**\n" +
                                String.join("\n", analysis.getQuestions())
                );
            }

            return AgentDecision.proceed("Logs analyzed successfully");

        } catch (Exception e) {
            log.error("Failed to analyze logs", e);
            return AgentDecision.proceed("Could not parse logs, continuing anyway");
        }
    }

    private String readLogs(WorkflowState state) throws Exception {
        StringBuilder sb = new StringBuilder();

        // Add pasted logs
        if (state.getLogsPasted() != null && !state.getLogsPasted().isEmpty()) {
            sb.append(state.getLogsPasted()).append("\n\n");
        }

        // Read attached log files
        if (state.getLogsAttached() != null) {
            for (FileUpload file : state.getLogsAttached()) {
                String content = Files.readString(new File(file.getFilePath()).toPath());
                sb.append("=== ").append(file.getFileName()).append(" ===\n");
                sb.append(content).append("\n\n");
            }
        }

        return sb.toString();
    }

    private LogAnalysis analyzeLogsWithLLM(WorkflowState state, String logContent) {
        // Prepare file data
        List<Map<String, Object>> fileData = new ArrayList<>();
        if (state.getLogsAttached() != null) {
            for (FileUpload file : state.getLogsAttached()) {
                fileData.add(Map.of(
                        "fileName", file.getFileName(),
                        "sizeBytes", file.getSizeBytes()
                ));
            }
        }

        // Render prompt using PROMPT LIBRARY
        Map<String, Object> variables = new HashMap<>();
        variables.put("logsPasted", logContent);
        variables.put("logsAttached", !fileData.isEmpty());
        variables.put("files", fileData);
        variables.put("requirement", state.getRequirement());
        variables.put("domain", state.getRequirementAnalysis() != null ?
                state.getRequirementAnalysis().getDomain() : "unknown");

        String prompt = promptLibrary.render("log-analyzer", variables);

        try {
            // Call LLM
            String jsonResponse = geminiClient.generateText(prompt);

            // Parse JSON response
            return objectMapper.readValue(jsonResponse, LogAnalysis.class);

        } catch (Exception e) {
            log.error("Failed to analyze logs with LLM", e);

            // Fallback
            return LogAnalysis.builder()
                    .errorType("Unknown")
                    .rootCauseHypothesis("Could not parse logs automatically")
                    .confidence(0.3)
                    .build();
        }
    }
}