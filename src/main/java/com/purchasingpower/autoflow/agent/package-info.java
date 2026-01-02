/**
 * Agent core: LLM agent with tool-based capabilities.
 *
 * <p>Implements the single-agent architecture with dynamic tool selection:
 * <ul>
 *   <li>Knowledge tools - search_code, query_graph, get_dependencies</li>
 *   <li>Understanding tools - explain_code, trace_flow, find_patterns</li>
 *   <li>Action tools - generate_code, modify_code, create_pr</li>
 * </ul>
 *
 * <p>Key classes:
 * <ul>
 *   <li>{@code AutoFlowAgent} - Main agent with tool selection</li>
 *   <li>{@code Tool} - Base interface for all tools</li>
 *   <li>{@code ToolExecutor} - Tool execution and result handling</li>
 *   <li>{@code ConversationManager} - Stateful conversation context</li>
 * </ul>
 *
 * @since 2.0.0
 */
package com.purchasingpower.autoflow.agent;
