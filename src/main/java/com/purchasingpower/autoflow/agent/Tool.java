package com.purchasingpower.autoflow.agent;

import java.util.Map;

/**
 * Base interface for agent tools.
 *
 * <p>Tools are the capabilities that the agent can invoke to accomplish tasks.
 * Each tool has a clear contract: input parameters, output format, and description
 * for the LLM to understand when to use it.
 *
 * <p>Example implementation:
 * <pre>
 * public class SearchCodeTool implements Tool {
 *     public String getName() { return "search_code"; }
 *
 *     public String getDescription() {
 *         return "Search for code using natural language query";
 *     }
 *
 *     public ToolResult execute(Map&lt;String, Object&gt; params) {
 *         String query = (String) params.get("query");
 *         // Execute search and return results
 *     }
 * }
 * </pre>
 *
 * @since 2.0.0
 */
public interface Tool {

    /**
     * Unique name for this tool (e.g., "search_code", "query_graph").
     * Used by the LLM to invoke the tool.
     */
    String getName();

    /**
     * Human-readable description for the LLM.
     * Explains what the tool does and when to use it.
     */
    String getDescription();

    /**
     * JSON schema for the tool's parameters.
     * Used by the LLM to construct valid tool calls.
     *
     * @return JSON schema string
     */
    String getParameterSchema();

    /**
     * Execute this tool with the given parameters.
     *
     * @param parameters Input parameters from the LLM
     * @param context Execution context (conversation, repositories)
     * @return Tool execution result
     */
    ToolResult execute(Map<String, Object> parameters, ToolContext context);

    /**
     * Category of this tool for organization.
     */
    ToolCategory getCategory();

    /**
     * Tool categories for organization and filtering.
     */
    enum ToolCategory {
        /**
         * Tools for finding and retrieving code.
         */
        KNOWLEDGE,

        /**
         * Tools for understanding code structure and behavior.
         */
        UNDERSTANDING,

        /**
         * Tools for modifying or creating code.
         */
        ACTION
    }
}
