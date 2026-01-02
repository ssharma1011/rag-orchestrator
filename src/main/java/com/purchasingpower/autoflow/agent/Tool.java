package com.purchasingpower.autoflow.agent;

import java.util.Map;

/**
 * Base interface for agent tools.
 *
 * @since 2.0.0
 */
public interface Tool {

    String getName();

    String getDescription();

    String getParameterSchema();

    ToolResult execute(Map<String, Object> parameters, ToolContext context);

    ToolCategory getCategory();

    /**
     * Whether this tool requires the repository to be indexed.
     * If true, IndexingInterceptor will auto-index before execution.
     *
     * @return true if tool needs indexed code, false otherwise
     */
    default boolean requiresIndexedRepo() {
        return false;
    }
}
