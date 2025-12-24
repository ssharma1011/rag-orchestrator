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
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogAnalyzerAgent {

    private final GeminiClient geminiClient;
    private final PromptLibraryService promptLibrary;
    private final ObjectMapper objectMapper;

    public Map<String, Object> execute(WorkflowState state) {
        if (!state.hasLogs()) {
            log.info("⏭️ No logs provided, skipping log analysis");
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("lastAgentDecision", AgentDecision.proceed("No logs to analyze"));
            return updates;
        }

        log.info("📋 Analyzing logs...");

        try {
            String logContent = readLogs(state);
            LogAnalysis analysis = analyzeLogsWithLLM(state, logContent);
            
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("logAnalysis", analysis);
            
            log.info("✅ Log analysis complete. Error type: {}, Confidence: {}",
                    analysis.getErrorType(), analysis.getConfidence());

            updates.put("lastAgentDecision", AgentDecision.proceed("Log analysis complete"));
            return updates;
            
        } catch (Exception e) {
            log.error("Log analysis failed", e);
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("lastAgentDecision", AgentDecision.error(e.getMessage()));
            return updates;
        }
    }

    private String readLogs(WorkflowState state) throws Exception {
        StringBuilder logContent = new StringBuilder();
        
        // Pasted logs
        if (state.getLogsPasted() != null && !state.getLogsPasted().isEmpty()) {
            logContent.append(state.getLogsPasted()).append("\n\n");
        }
        
        if (state.getLogsAttached() != null) {
            for (FileUpload logFile : state.getLogsAttached()) {
                File file = new File(logFile.getFilePath());  // FIX: was getTempPath()
                String content = Files.readString(file.toPath());
                logContent.append("=== ").append(logFile.getFileName()).append(" ===\n");
                logContent.append(content).append("\n\n");
            }
        }
        
        return logContent.toString();
    }

    private LogAnalysis analyzeLogsWithLLM(WorkflowState state, String logContent) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requirement", state.getRequirement());
        variables.put("logContent", logContent);
        variables.put("taskType", state.getRequirementAnalysis() != null ? 
                state.getRequirementAnalysis().getTaskType() : "unknown");
        variables.put("domain", state.getRequirementAnalysis() != null ? 
                state.getRequirementAnalysis().getDomain() : "unknown");

        String prompt = promptLibrary.render("log-analyzer", variables);

        try {
            String jsonResponse = geminiClient.generateText(prompt);
            return objectMapper.readValue(jsonResponse, LogAnalysis.class);
        } catch (Exception e) {
            log.error("Failed to analyze logs with LLM", e);
            return LogAnalysis.builder()
                    .errorType("Unknown")
                    .rootCauseHypothesis("Could not parse logs automatically")
                    .confidence(0.3)
                    .build();
        }
    }
}
