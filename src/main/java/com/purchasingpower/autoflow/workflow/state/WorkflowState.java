package com.purchasingpower.autoflow.workflow.state;

import org.bsc.langgraph4j.state.AgentState;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WorkflowState for LangGraph4J workflow orchestration.
 *
 * Extends AgentState and provides ALL setters used across the codebase:
 * - AutoFlowWorkflow uses: setWorkflowStatus, setCurrentAgent, setLastAgentDecision, setBuildAttempt, setReviewAttempt
 * - WorkflowExecutionServiceImpl uses: setConversationId, setWorkflowStatus
 * - WorkflowController uses: addChatMessage
 * - Agents use: setBuildResult, setLogAnalysis, setRequirementAnalysis, setScopeProposal, setContext, etc.
 */
public class WorkflowState extends AgentState {

    public WorkflowState(Map<String, Object> initData) {
        super(initData);
    }

    // ================================================================
    // GETTERS
    // ================================================================

    public String getConversationId() {
        return this.<String>value("conversationId").orElse(null);
    }

    public String getUserId() {
        return this.<String>value("userId").orElse(null);
    }

    public String getRequirement() {
        return this.<String>value("requirement").orElse(null);
    }

    public String getJiraUrl() {
        return this.<String>value("jiraUrl").orElse(null);
    }

    public String getTargetClass() {
        return this.<String>value("targetClass").orElse(null);
    }

    public String getRepoUrl() {
        return this.<String>value("repoUrl").orElse(null);
    }

    public String getBaseBranch() {
        return this.<String>value("baseBranch").orElse("main");
    }

    public String getLogsPasted() {
        return this.<String>value("logsPasted").orElse(null);
    }

    @SuppressWarnings("unchecked")
    public List<FileUpload> getLogsAttached() {
        return this.<List<FileUpload>>value("logsAttached").orElse(new ArrayList<>());
    }

    public String getWorkflowStatus() {
        return this.<String>value("workflowStatus").orElse(null);
    }

    public String getCurrentAgent() {
        return this.<String>value("currentAgent").orElse(null);
    }

    public int getBuildAttempt() {
        return this.<Integer>value("buildAttempt").orElse(0);
    }

    public int getReviewAttempt() {
        return this.<Integer>value("reviewAttempt").orElse(0);
    }

    public String getCurrentCommit() {
        return this.<String>value("currentCommit").orElse(null);
    }

    public String getPrDescription() {
        return this.<String>value("prDescription").orElse(null);
    }

    public String getPrUrl() {
        return this.<String>value("prUrl").orElse(null);
    }

    public String getBranchName() {
        return this.<String>value("branchName").orElse(null);
    }

    public File getWorkspaceDir() {
        return this.<File>value("workspaceDir").orElse(null);
    }

    public AgentDecision getLastAgentDecision() {
        return this.<AgentDecision>value("lastAgentDecision").orElse(null);
    }

    public RequirementAnalysis getRequirementAnalysis() {
        return this.<RequirementAnalysis>value("requirementAnalysis").orElse(null);
    }

    public LogAnalysis getLogAnalysis() {
        return this.<LogAnalysis>value("logAnalysis").orElse(null);
    }

    public ScopeProposal getScopeProposal() {
        return this.<ScopeProposal>value("scopeProposal").orElse(null);
    }

    public StructuredContext getContext() {
        return this.<StructuredContext>value("context").orElse(null);
    }

    public com.purchasingpower.autoflow.model.llm.CodeGenerationResponse getGeneratedCode() {
        return this.<com.purchasingpower.autoflow.model.llm.CodeGenerationResponse>value("generatedCode").orElse(null);
    }

    public BuildResult getBuildResult() {
        return this.<BuildResult>value("buildResult").orElse(null);
    }

    public BuildResult getBaselineBuild() {
        return this.<BuildResult>value("baselineBuild").orElse(null);
    }

    public TestResult getTestResult() {
        return this.<TestResult>value("testResult").orElse(null);
    }

    public CodeReview getCodeReview() {
        return this.<CodeReview>value("codeReview").orElse(null);
    }

    public IndexingResult getIndexingResult() {
        return this.<IndexingResult>value("indexingResult").orElse(null);
    }

    @SuppressWarnings("unchecked")
    public List<ChatMessage> getConversationHistory() {
        return this.<List<ChatMessage>>value("conversationHistory").orElse(new ArrayList<>());
    }

    public Object getParsedCode() {
        return value("parsedCode").orElse(null);
    }

    // ================================================================
    // SETTERS - Used by AutoFlowWorkflow, Agents, and Services
    // ================================================================

    // Used by: WorkflowExecutionServiceImpl.startWorkflow()
    public void setConversationId(String id) { data().put("conversationId", id); }

    // Used by: AutoFlowWorkflow.execute(), AutoFlowWorkflow.ask_developer node, WorkflowExecutionServiceImpl
    public void setWorkflowStatus(String status) { data().put("workflowStatus", status); }

    // Used by: AutoFlowWorkflow.executeAgent()
    public void setCurrentAgent(String agent) { data().put("currentAgent", agent); }

    // Used by: AutoFlowWorkflow.executeAgent()
    public void setLastAgentDecision(AgentDecision decision) { data().put("lastAgentDecision", decision); }

    // Used by: AutoFlowWorkflow.execute() initialization
    public void setBuildAttempt(int attempt) { data().put("buildAttempt", attempt); }

    // Used by: AutoFlowWorkflow.execute() initialization
    public void setReviewAttempt(int attempt) { data().put("reviewAttempt", attempt); }

    // Used by: Agents
    public void setRequirementAnalysis(RequirementAnalysis analysis) { data().put("requirementAnalysis", analysis); }
    public void setLogAnalysis(LogAnalysis analysis) { data().put("logAnalysis", analysis); }
    public void setScopeProposal(ScopeProposal proposal) { data().put("scopeProposal", proposal); }
    public void setContext(StructuredContext context) { data().put("context", context); }
    public void setGeneratedCode(com.purchasingpower.autoflow.model.llm.CodeGenerationResponse code) { data().put("generatedCode", code); }
    public void setBuildResult(BuildResult result) { data().put("buildResult", result); }
    public void setBaselineBuild(BuildResult result) { data().put("baselineBuild", result); }
    public void setTestResult(TestResult result) { data().put("testResult", result); }
    public void setCodeReview(CodeReview review) { data().put("codeReview", review); }
    public void setIndexingResult(IndexingResult result) { data().put("indexingResult", result); }
    public void setPrUrl(String url) { data().put("prUrl", url); }
    public void setPrDescription(String desc) { data().put("prDescription", desc); }
    public void setWorkspaceDir(File dir) { data().put("workspaceDir", dir); }
    public void setConversationHistory(List<ChatMessage> history) { data().put("conversationHistory", history); }

    // ================================================================
    // HELPER METHODS
    // ================================================================

    /**
     * Used by: AutoFlowWorkflow.routeFromBuildValidator()
     */
    public void incrementBuildAttempt() {
        data().put("buildAttempt", getBuildAttempt() + 1);
    }

    /**
     * Used by: AutoFlowWorkflow.routeFromPRReviewer()
     */
    public void incrementReviewAttempt() {
        data().put("reviewAttempt", getReviewAttempt() + 1);
    }

    /**
     * Used by: WorkflowController.respondToPrompt()
     */
    public void addChatMessage(String role, String content) {
        List<ChatMessage> history = new ArrayList<>(getConversationHistory());
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        message.setTimestamp(java.time.LocalDateTime.now());
        history.add(message);
        data().put("conversationHistory", history);
    }

    public boolean hasLogs() {
        String logs = getLogsPasted();
        boolean hasPasted = logs != null && !logs.isBlank();
        boolean hasAttached = getLogsAttached() != null && !getLogsAttached().isEmpty();
        return hasPasted || hasAttached;
    }

    public int getTotalFilesInScope() {
        ScopeProposal scope = getScopeProposal();
        return scope != null ? scope.getTotalFileCount() : 0;
    }

    // ================================================================
    // BUILDER
    // ================================================================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final HashMap<String, Object> data = new HashMap<>();

        public Builder conversationId(String id) { data.put("conversationId", id); return this; }
        public Builder userId(String id) { data.put("userId", id); return this; }
        public Builder requirement(String req) { data.put("requirement", req); return this; }
        public Builder targetClass(String cls) { data.put("targetClass", cls); return this; }
        public Builder repoUrl(String url) { data.put("repoUrl", url); return this; }
        public Builder baseBranch(String branch) { data.put("baseBranch", branch); return this; }
        public Builder logsPasted(String logs) { data.put("logsPasted", logs); return this; }
        public Builder jiraUrl(String url) { data.put("jiraUrl", url); return this; }

        public WorkflowState build() {
            return new WorkflowState(data);
        }
    }

    // ================================================================
    // CONVERSION METHODS
    // ================================================================

    public Map<String, Object> toMap() {
        return data();
    }

    public static WorkflowState fromMap(Map<String, Object> map) {
        return new WorkflowState(map);
    }
}