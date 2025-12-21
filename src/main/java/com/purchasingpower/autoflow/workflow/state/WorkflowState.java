package com.purchasingpower.autoflow.workflow.state;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import java.io.File;
import java.util.Map;

/**
 * COMPLETE WorkflowState for LangGraph4J - FIXED CHANNEL API.
 *
 * REPLACES your current WorkflowState.java entirely.
 *
 * KEY FIX: Uses Channels.lastValue() which is the actual LangGraph4J API
 */
public class WorkflowState extends AgentState {

    // ================================================================
    // SCHEMA (Required by LangGraph4J)
    // Uses Channels.lastValue() - the CORRECT API
    // ================================================================

    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
            // Simple fields - last write wins
            Map.entry("conversationId", Channels.lastValue(String.class)),
            Map.entry("userId", Channels.lastValue(String.class)),
            Map.entry("requirement", Channels.lastValue(String.class)),
            Map.entry("jiraUrl", Channels.lastValue(String.class)),
            Map.entry("targetClass", Channels.lastValue(String.class)),
            Map.entry("repoUrl", Channels.lastValue(String.class)),
            Map.entry("baseBranch", Channels.lastValue(String.class)),
            Map.entry("logsPasted", Channels.lastValue(String.class)),
            Map.entry("workflowStatus", Channels.lastValue(String.class)),
            Map.entry("currentAgent", Channels.lastValue(String.class)),
            Map.entry("buildAttempt", Channels.lastValue(Integer.class)),
            Map.entry("reviewAttempt", Channels.lastValue(Integer.class)),
            Map.entry("currentCommit", Channels.lastValue(String.class)),
            Map.entry("prDescription", Channels.lastValue(String.class)),
            Map.entry("prUrl", Channels.lastValue(String.class)),
            Map.entry("branchName", Channels.lastValue(String.class)),
            Map.entry("workspaceDir", Channels.lastValue(File.class))
    );

    // ================================================================
    // CONSTRUCTOR (Required by LangGraph4J)
    // ================================================================

    public WorkflowState(Map<String, Object> initData) {
        super(initData);
    }

    // ================================================================
    // ALL YOUR EXISTING GETTERS - Using AgentState.value()
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

    public String getWorkflowStatus() {
        return this.<String>value("workflowStatus").orElse("PENDING");
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

    // ================================================================
    // ALL YOUR EXISTING SETTERS - Using AgentState.data().put()
    // ================================================================

    public void setConversationId(String id) {
        data().put("conversationId", id);
    }

    public void setWorkflowStatus(String status) {
        data().put("workflowStatus", status);
    }

    public void setCurrentAgent(String agent) {
        data().put("currentAgent", agent);
    }

    public void setBuildAttempt(int attempt) {
        data().put("buildAttempt", attempt);
    }

    public void setReviewAttempt(int attempt) {
        data().put("reviewAttempt", attempt);
    }

    public void setLastAgentDecision(AgentDecision decision) {
        data().put("lastAgentDecision", decision);
    }

    public void setRequirementAnalysis(RequirementAnalysis analysis) {
        data().put("requirementAnalysis", analysis);
    }

    public void setLogAnalysis(LogAnalysis analysis) {
        data().put("logAnalysis", analysis);
    }

    public void setScopeProposal(ScopeProposal proposal) {
        data().put("scopeProposal", proposal);
    }

    public void setContext(StructuredContext context) {
        data().put("context", context);
    }

    public void setGeneratedCode(com.purchasingpower.autoflow.model.llm.CodeGenerationResponse code) {
        data().put("generatedCode", code);
    }

    public void setBuildResult(BuildResult result) {
        data().put("buildResult", result);
    }

    public void setBaselineBuild(BuildResult result) {
        data().put("baselineBuild", result);
    }

    public void setTestResult(TestResult result) {
        data().put("testResult", result);
    }

    public void setCodeReview(CodeReview review) {
        data().put("codeReview", review);
    }

    public void setPrDescription(String description) {
        data().put("prDescription", description);
    }

    public void setPrUrl(String url) {
        data().put("prUrl", url);
    }

    public void setBranchName(String name) {
        data().put("branchName", name);
    }

    public void setWorkspaceDir(File dir) {
        data().put("workspaceDir", dir);
    }

    public void setIndexingResult(IndexingResult result) {
        data().put("indexingResult", result);
    }

    // ================================================================
    // HELPER METHODS (All your existing ones)
    // ================================================================

    public boolean hasLogs() {
        String logs = getLogsPasted();
        return logs != null && !logs.isBlank();
    }

    public int getTotalFilesInScope() {
        ScopeProposal scope = getScopeProposal();
        return scope != null ? scope.getTotalFileCount() : 0;
    }

    // ================================================================
    // BUILDER (For initial construction only)
    // ================================================================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final java.util.HashMap<String, Object> data = new java.util.HashMap<>();

        public Builder conversationId(String id) {
            data.put("conversationId", id);
            return this;
        }

        public Builder userId(String id) {
            data.put("userId", id);
            return this;
        }

        public Builder requirement(String req) {
            data.put("requirement", req);
            return this;
        }

        public Builder targetClass(String cls) {
            data.put("targetClass", cls);
            return this;
        }

        public Builder repoUrl(String url) {
            data.put("repoUrl", url);
            return this;
        }

        public Builder baseBranch(String branch) {
            data.put("baseBranch", branch);
            return this;
        }

        public Builder logsPasted(String logs) {
            data.put("logsPasted", logs);
            return this;
        }

        public WorkflowState build() {
            return new WorkflowState(data);
        }
    }
}