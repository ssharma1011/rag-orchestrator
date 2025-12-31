package com.purchasingpower.autoflow.agent;

import java.util.Map;

/**
 * Result from a tool execution.
 *
 * <p>Provides a structured way to return tool results to the agent,
 * including success/failure status, data, and any follow-up suggestions.
 *
 * @since 2.0.0
 */
public interface ToolResult {

    /**
     * Whether the tool executed successfully.
     */
    boolean isSuccess();

    /**
     * The primary result data.
     * Type depends on the tool (search results, code, explanation, etc.)
     */
    Object getData();

    /**
     * Human-readable message about the result.
     * Used for logging and debugging.
     */
    String getMessage();

    /**
     * Additional metadata about the execution.
     */
    Map<String, Object> getMetadata();

    /**
     * Suggested next tools the agent might want to call.
     * Helps guide the agent's decision making.
     */
    String[] getSuggestedNextTools();

    /**
     * Create a successful result.
     */
    static ToolResult success(Object data, String message) {
        return new ToolResultImpl(true, data, message, Map.of(), new String[0]);
    }

    /**
     * Create a failed result.
     */
    static ToolResult failure(String message) {
        return new ToolResultImpl(false, null, message, Map.of(), new String[0]);
    }
}

/**
 * Default implementation of ToolResult.
 */
record ToolResultImpl(
    boolean isSuccess,
    Object data,
    String message,
    Map<String, Object> metadata,
    String[] suggestedNextTools
) implements ToolResult {

    @Override
    public boolean isSuccess() {
        return isSuccess;
    }

    @Override
    public Object getData() {
        return data;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public String[] getSuggestedNextTools() {
        return suggestedNextTools;
    }
}
