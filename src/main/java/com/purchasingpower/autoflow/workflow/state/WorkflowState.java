package com.purchasingpower.autoflow.workflow.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.purchasingpower.autoflow.model.WorkflowStatus;
import org.bsc.langgraph4j.state.AgentState;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WorkflowState for LangGraph4J workflow orchestration.
 *
 * All complex object getters handle LinkedHashMap deserialization
 * that occurs when ObjectMapper loads state from database as Map.class.
 *
 * Extends AgentState and provides ALL setters used across the codebase:
 * - AutoFlowWorkflow uses: setWorkflowStatus, setCurrentAgent, setLastAgentDecision, setBuildAttempt, setReviewAttempt
 * - WorkflowExecutionServiceImpl uses: setConversationId, setWorkflowStatus
 * - WorkflowController uses: addChatMessage
 * - Agents use: setBuildResult, setLogAnalysis, setRequirementAnalysis, setScopeProposal, setContext, etc.
 */
public class WorkflowState extends AgentState {

    /**
     * ObjectMapper configured with JavaTimeModule for proper LocalDateTime handling.
     * This fixes conversation history deserialization issues.
     */
    private static final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    public WorkflowState(Map<String, Object> initData) {
        super(initData);
    }

    // ================================================================
    // SIMPLE GETTERS (No deserialization issues)
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

    /**
     * Get current workflow status as enum.
     * Safely converts from string stored in state map.
     *
     * @return WorkflowStatus enum, or null if not set
     */
    public WorkflowStatus getWorkflowStatus() {
        String statusStr = this.<String>value("workflowStatus").orElse(null);
        if (statusStr == null) {
            return null;
        }
        try {
            return WorkflowStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            // Invalid status string - log warning and return null
            return null;
        }
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
        Object value = data().get("workspaceDir");
        if (value == null) return null;
        if (value instanceof File) return (File) value;
        if (value instanceof String) return new File((String) value);  // ← Handle String!
        return null;
    }

    public void setWorkspaceDir(File dir) {
        if (dir != null) {
            data().put("workspaceDir", dir.getAbsolutePath());  // ← Store as String
        } else {
            data().put("workspaceDir", null);
        }
    }

    // ================================================================
    // COMPLEX OBJECT GETTERS (FIXED for LinkedHashMap deserialization)
    // ================================================================

    /**
     * FIXED: Safely handles LinkedHashMap deserialization from database.
     *
     * When WorkflowState is saved to DB as JSON and then loaded back,
     * ObjectMapper.readValue(json, Map.class) creates LinkedHashMaps for
     * all nested objects instead of their proper types.
     */
    public AgentDecision getLastAgentDecision() {
        return convertValue("lastAgentDecision", AgentDecision.class);
    }

    public RequirementAnalysis getRequirementAnalysis() {
        return convertValue("requirementAnalysis", RequirementAnalysis.class);
    }

    public LogAnalysis getLogAnalysis() {
        return convertValue("logAnalysis", LogAnalysis.class);
    }

    public ScopeProposal getScopeProposal() {
        return convertValue("scopeProposal", ScopeProposal.class);
    }

    public StructuredContext getContext() {
        return convertValue("context", StructuredContext.class);
    }

    public com.purchasingpower.autoflow.model.llm.CodeGenerationResponse getGeneratedCode() {
        return convertValue("generatedCode", com.purchasingpower.autoflow.model.llm.CodeGenerationResponse.class);
    }

    public BuildResult getBuildResult() {
        return convertValue("buildResult", BuildResult.class);
    }

    public BuildResult getBaselineBuild() {
        return convertValue("baselineBuild", BuildResult.class);
    }

    public TestResult getTestResult() {
        return convertValue("testResult", TestResult.class);
    }

    public CodeReview getCodeReview() {
        return convertValue("codeReview", CodeReview.class);
    }

    public IndexingResult getIndexingResult() {
        return convertValue("indexingResult", IndexingResult.class);
    }

    public Object getParsedCode() {
        return value("parsedCode").orElse(null);
    }

    // ================================================================
    // LIST GETTERS (Also need safe deserialization)
    // ================================================================

    @SuppressWarnings("unchecked")
    public List<FileUpload> getLogsAttached() {
        return convertList("logsAttached", FileUpload.class);
    }

    @SuppressWarnings("unchecked")
    public List<ChatMessage> getConversationHistory() {
        return convertList("conversationHistory", ChatMessage.class);
    }

    // ================================================================
    // HELPER METHODS FOR SAFE DESERIALIZATION
    // ================================================================

    /**
     * Helper method to safely convert Map to typed object.
     *
     * Handles three cases:
     * 1. Object is already the correct type → return as-is
     * 2. Object is a Map (LinkedHashMap from JSON) → convert using ObjectMapper
     * 3. Object is null or incompatible type → return null
     */
    private <T> T convertValue(String key, Class<T> type) {
        Object obj = value(key).orElse(null);

        if (obj == null) {
            return null;
        }

        // Already correct type
        if (type.isInstance(obj)) {
            return type.cast(obj);
        }

        // Map (LinkedHashMap from JSON deserialization)
        if (obj instanceof Map) {
            try {
                return objectMapper.convertValue(obj, type);
            } catch (Exception e) {
                // Conversion failed, return null
                return null;
            }
        }

        // Unknown type
        return null;
    }

    /**
     * Helper method to safely convert List<Map> to List<T>.
     *
     * When lists of complex objects are deserialized from JSON,
     * each element becomes a LinkedHashMap.
     */
    private <T> List<T> convertList(String key, Class<T> elementType) {
        Object obj = value(key).orElse(null);

        if (obj == null) {
            return new ArrayList<>();
        }

        if (!(obj instanceof List)) {
            return new ArrayList<>();
        }

        List<?> rawList = (List<?>) obj;
        List<T> result = new ArrayList<>();

        for (Object item : rawList) {
            if (item == null) {
                continue;
            }

            if (elementType.isInstance(item)) {
                result.add(elementType.cast(item));
            } else if (item instanceof Map) {
                try {
                    T converted = objectMapper.convertValue(item, elementType);
                    result.add(converted);
                } catch (Exception e) {
                    // Skip items that fail conversion
                }
            }
        }

        return result;
    }

    // ================================================================
    // CONVENIENCE METHODS
    // ================================================================

    public boolean hasLogs() {
        return getLogsPasted() != null ||
                (getLogsAttached() != null && !getLogsAttached().isEmpty());
    }

    public int getTotalFileCount() {
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
