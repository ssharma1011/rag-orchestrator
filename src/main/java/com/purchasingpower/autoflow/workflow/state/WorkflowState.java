package com.purchasingpower.autoflow.workflow.state;

import com.purchasingpower.autoflow.model.ast.CodeChunk;
import com.purchasingpower.autoflow.model.llm.CodeGenerationResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete state object for LangGraph4J workflow.
 * This flows through all agent nodes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowState {

    // ================================================================
    // INPUT (from chat UI)
    // ================================================================

    private String conversationId;
    private String userId;

    // What user wants
    private String requirement;        // "Fix payment bug" or "Add retry logic"
    private String jiraUrl;           // Optional - if provided, fetch details
    private String targetClass;       // MANDATORY - "Which class to modify?"
    private String repoUrl;           // Git repository URL
    private String baseBranch;        // "main" or "develop"

    // Logs (if bug fix)
    private String logsPasted;        // User pasted in chat
    @Builder.Default
    private List<FileUpload> logsAttached = new ArrayList<>();

    // ================================================================
    // CONVERSATION
    // ================================================================

    @Builder.Default
    private List<ChatMessage> conversationHistory = new ArrayList<>();

    @Builder.Default
    private Map<String, String> clarifications = new HashMap<>();  // Question → Answer

    // ================================================================
    // WORKSPACE
    // ================================================================

    private File workspaceDir;        // Where repo is cloned
    private String currentCommit;     // HEAD commit SHA

    // ================================================================
    // REQUIREMENT ANALYSIS
    // ================================================================

    private RequirementAnalysis requirementAnalysis;

    private LogAnalysis logAnalysis;

    // ================================================================
    // CODE INDEXING
    // ================================================================

    @Builder.Default
    private List<CodeChunk> parsedCode = new ArrayList<>();

    private IndexingResult indexingResult;

    // Baseline build (BEFORE any changes)
    private BuildResult baselineBuild;


    private ScopeProposal scopeProposal;

    private StructuredContext context;

    private CodeGenerationResponse generatedCode;

    // Build/test after code generation
    private BuildResult buildResult;
    private TestResult testResult;

    private CodeReview reviewResult;

    @Builder.Default
    private int regenerationAttempt = 0;  // How many times we've regenerated

    // ================================================================
    // OUTPUT
    // ================================================================

    private String prUrl;
    private String readmeContent;

    // ================================================================
    // METADATA
    // ================================================================

    private String currentNode;       // Which agent is executing
    private AgentDecision lastDecision;
    private double overallConfidence;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();  // Flexible extension point

    // ================================================================
    // HELPER METHODS
    // ================================================================

    public boolean hasLogs() {
        return (logsPasted != null && !logsPasted.isEmpty()) ||
                (logsAttached != null && !logsAttached.isEmpty());
    }

    public boolean requiresRegeneration() {
        return regenerationAttempt < 3 &&
                (buildResult != null && !buildResult.isSuccess() ||
                        testResult != null && !testResult.isAllTestsPassed() ||
                        reviewResult != null && !reviewResult.isApproved());
    }

    public void addMessage(String role, String content) {
        conversationHistory.add(new ChatMessage(role, content, System.currentTimeMillis()));
    }
}