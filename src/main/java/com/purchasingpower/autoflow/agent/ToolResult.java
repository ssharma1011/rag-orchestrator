package com.purchasingpower.autoflow.agent;

import java.util.Map;

/**
 * Result from a tool execution.
 *
 * @since 2.0.0
 */
public interface ToolResult {

    boolean isSuccess();

    Object getData();

    String getMessage();

    Map<String, Object> getMetadata();

    String[] getSuggestedNextTools();

    static ToolResult success(Object data, String message) {
        return new DefaultToolResult(true, data, message, Map.of(), new String[0]);
    }

    static ToolResult failure(String message) {
        return new DefaultToolResult(false, null, message, Map.of(), new String[0]);
    }
}
