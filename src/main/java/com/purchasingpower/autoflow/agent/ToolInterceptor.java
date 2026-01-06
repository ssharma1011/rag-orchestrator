package com.purchasingpower.autoflow.agent;

/**
 * Interceptor for tool execution.
 *
 * Allows pre/post processing around tool execution.
 * Used for cross-cutting concerns like ensuring repo is indexed.
 *
 * @since 2.0.0
 */
public interface ToolInterceptor {

    /**
     * Called before tool execution.
     * Can perform validation, setup, or trigger prerequisite actions.
     *
     * @param tool The tool about to be executed
     * @param context The execution context
     * @throws RuntimeException if pre-conditions fail
     */
    void beforeExecute(Tool tool, ToolContext context);

    /**
     * Called after tool execution.
     * Can perform cleanup or logging.
     *
     * @param tool The tool that was executed
     * @param context The execution context
     * @param result The result from the tool
     */
    default void afterExecute(Tool tool, ToolContext context, ToolResult result) {
        // Optional - override if needed
    }

    /**
     * Check if this interceptor applies to the given tool.
     *
     * @param tool The tool to check
     * @return true if interceptor should run for this tool
     */
    boolean appliesTo(Tool tool);
}
