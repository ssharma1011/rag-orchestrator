package com.purchasingpower.autoflow.agent.impl;

import com.purchasingpower.autoflow.agent.ToolContext;
import com.purchasingpower.autoflow.model.conversation.Conversation;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of ToolContext.
 *
 * @since 2.0.0
 */
@Data
@Builder
public class ToolContextImpl implements ToolContext {

    private Conversation conversation;

    @Builder.Default
    private List<String> repositoryIds = new ArrayList<>();

    private String repositoryUrl;

    private String branch;

    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();

    @Builder.Default
    private List<Object> recentSearchResults = new ArrayList<>();

    @Builder.Default
    private List<Object> recentModifications = new ArrayList<>();

    @Override
    public Object getVariable(String key) {
        return variables.get(key);
    }

    @Override
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    @Override
    public List<?> getRecentSearchResults() {
        return recentSearchResults;
    }

    @Override
    public List<?> getRecentModifications() {
        return recentModifications;
    }

    public void addSearchResult(Object result) {
        recentSearchResults.add(result);
        if (recentSearchResults.size() > 100) {
            recentSearchResults.remove(0);
        }
    }

    public void addModification(Object modification) {
        recentModifications.add(modification);
        if (recentModifications.size() > 50) {
            recentModifications.remove(0);
        }
    }

    @Override
    public void recordToolExecution(String toolName, Object result, String userFeedback) {
        Map<String, Object> execution = new HashMap<>();
        execution.put("tool", toolName);
        execution.put("timestamp", System.currentTimeMillis());
        execution.put("result", result);
        execution.put("userFeedback", userFeedback);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) getVariable("tool_execution_history");
        if (history == null) {
            history = new ArrayList<>();
            setVariable("tool_execution_history", history);
        }
        history.add(execution);

        // Keep only last 50 executions
        if (history.size() > 50) {
            history.remove(0);
        }
    }

    @Override
    public int getToolExecutionCount(String toolName) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) getVariable("tool_execution_history");
        if (history == null) {
            return 0;
        }
        return (int) history.stream()
            .filter(e -> toolName.equals(e.get("tool")))
            .count();
    }

    @Override
    public boolean hasNegativeFeedback() {
        if (conversation == null || conversation.getMessages() == null) {
            return false;
        }

        // Check last 3 user messages for improvement requests
        String[] feedbackPhrases = {
            "better", "more detail", "improve", "different", "expand",
            "deeper", "comprehensive", "thorough", "enhanced", "refined"
        };

        return conversation.getMessages().stream()
            .filter(msg -> "user".equals(msg.getRole()))
            .limit(3)
            .anyMatch(msg -> {
                String content = msg.getContent().toLowerCase();
                for (String phrase : feedbackPhrases) {
                    if (content.contains(phrase)) {
                        return true;
                    }
                }
                return false;
            });
    }

    @Override
    public Object getLastToolResult(String toolName) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) getVariable("tool_execution_history");
        if (history == null || history.isEmpty()) {
            return null;
        }

        // Find last execution of this tool
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, Object> execution = history.get(i);
            if (toolName.equals(execution.get("tool"))) {
                return execution.get("result");
            }
        }
        return null;
    }

    public static ToolContextImpl create(Conversation conversation) {
        List<String> repoIds = new ArrayList<>();
        if (conversation.getRepoName() != null) {
            repoIds.add(conversation.getRepoName());
        }
        return ToolContextImpl.builder()
            .conversation(conversation)
            .repositoryIds(repoIds)
            .repositoryUrl(conversation.getRepoUrl())
            .branch(extractBranchFromUrl(conversation.getRepoUrl()))
            .build();
    }

    public static ToolContextImpl create(Conversation conversation, List<String> repositoryIds) {
        return ToolContextImpl.builder()
            .conversation(conversation)
            .repositoryIds(new ArrayList<>(repositoryIds))
            .repositoryUrl(conversation.getRepoUrl())
            .branch(extractBranchFromUrl(conversation.getRepoUrl()))
            .build();
    }

    private static String extractBranchFromUrl(String url) {
        if (url == null) return "main";
        if (url.contains("/tree/")) {
            String[] parts = url.split("/tree/");
            if (parts.length > 1) {
                return parts[1].split("/")[0];
            }
        }
        return "main";
    }
}
