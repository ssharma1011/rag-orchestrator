package com.purchasingpower.autoflow.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.agent.impl.ToolContextImpl;
import com.purchasingpower.autoflow.client.LLMProvider;
import com.purchasingpower.autoflow.client.LLMProviderFactory;
import com.purchasingpower.autoflow.model.conversation.Conversation;
import com.purchasingpower.autoflow.model.conversation.ConversationMessage;
import com.purchasingpower.autoflow.model.dto.WorkflowResponse;
import com.purchasingpower.autoflow.service.ChatStreamService;
import com.purchasingpower.autoflow.service.ConversationService;
import com.purchasingpower.autoflow.service.PromptLibraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AutoFlowAgent - Single flexible agent with tools.
 *
 * Replaces the 13 workflow agents with a unified tool-based approach.
 * Uses LLM to decide which tools to invoke based on user intent.
 *
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoFlowAgent {

    private final LLMProviderFactory llmProviderFactory;
    private final ConversationService conversationService;
    private final List<Tool> tools;
    private final List<ToolInterceptor> interceptors;
    private final ObjectMapper objectMapper;
    private final PromptLibraryService promptLibrary;

    @Autowired(required = false)
    private ChatStreamService streamService;

    private static final int MAX_TOOL_ITERATIONS = 10; // Allow comprehensive multi-step analysis

    public WorkflowResponse process(String userMessage, String conversationId, String userId) {
        log.info("Processing message for conversation: {}", conversationId);
        long startTime = System.currentTimeMillis();

        try {
            // Save user message via service (handles transaction properly)
            conversationService.addMessage(conversationId, "user", userMessage);

            // Fetch conversation for context (fresh fetch with session)
            Conversation conversation = getOrCreateConversation(conversationId, userId);

            // Create context with repo URL and branch for interceptors
            ToolContextImpl context = ToolContextImpl.create(conversation);

            String response = runAgentLoop(userMessage, conversation, context);

            // Save assistant response via service (handles transaction properly)
            conversationService.addMessage(conversationId, "assistant", response);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Processed in {}ms", duration);

            return WorkflowResponse.builder()
                .success(true)
                .conversationId(conversationId)
                .message(response)
                .status("COMPLETED")
                .build();

        } catch (Exception e) {
            log.error("Agent processing failed", e);
            return WorkflowResponse.builder()
                .success(false)
                .conversationId(conversationId)
                .error(e.getMessage())
                .status("FAILED")
                .build();
        }
    }

    private String runAgentLoop(String userMessage, Conversation conversation, ToolContextImpl context) {
        String conversationId = conversation.getConversationId();
        String currentPrompt = buildInitialPrompt(userMessage, conversation);
        List<String> toolsUsed = new ArrayList<>();

        // Send initial thinking event
        emitEvent(conversationId, "thinking", "Analyzing your request...");

        for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
            emitEvent(conversationId, "thinking", "Processing...");

            // Use Ollama for tool selection (fast, local)
            LLMProvider toolLlm = llmProviderFactory.getToolSelectionProvider();
            String llmResponse = toolLlm.chat(currentPrompt, "AutoFlowAgent-ToolSelection", conversationId);

            Optional<ToolCall> toolCall = parseToolCall(llmResponse);
            if (toolCall.isEmpty()) {
                // No more tools needed - generate final response with Gemini (high quality)
                log.info("Tool selection complete. Generating final response with quality provider...");
                emitEvent(conversationId, "thinking", "Generating final response...");

                LLMProvider finalLlm = llmProviderFactory.getFinalResponseProvider();
                String finalPrompt = buildFinalResponsePrompt(userMessage, conversation, toolsUsed);
                String finalResponse = finalLlm.chat(finalPrompt, "AutoFlowAgent-FinalResponse", conversationId);

                String response = extractFinalResponse(finalResponse);
                emitComplete(conversationId, response);
                return response;
            }

            ToolCall call = toolCall.get();
            log.info("Executing tool: {}", call.toolName);
            toolsUsed.add(call.toolName);

            // Emit tool execution event
            emitToolEvent(conversationId, call.toolName, "Executing...");

            ToolResult result = executeTool(call.toolName, call.parameters, context);

            // Emit tool result event
            emitToolEvent(conversationId, call.toolName, result.isSuccess() ? "Completed" : "Failed");

            currentPrompt = buildFollowUpPrompt(userMessage, conversation, call, result);
        }

        // Max iterations reached - generate final response with quality provider
        log.info("Max tool iterations reached. Generating final response...");
        emitEvent(conversationId, "thinking", "Generating final response...");

        LLMProvider finalLlm = llmProviderFactory.getFinalResponseProvider();
        String finalPrompt = buildFinalResponsePrompt(userMessage, conversation, toolsUsed);
        String finalResponse = finalLlm.chat(finalPrompt, "AutoFlowAgent-FinalResponse", conversationId);

        String response = extractFinalResponse(finalResponse);
        emitComplete(conversationId, response);
        return response;
    }

    private void emitEvent(String conversationId, String type, String message) {
        if (streamService != null) {
            streamService.sendThinking(conversationId, message);
        }
    }

    private void emitToolEvent(String conversationId, String toolName, String status) {
        if (streamService != null) {
            streamService.sendToolExecution(conversationId, toolName, status);
        }
    }

    private void emitComplete(String conversationId, String response) {
        if (streamService != null) {
            streamService.sendComplete(conversationId, response);
        }
    }

    private String buildInitialPrompt(String userMessage, Conversation conversation) {
        // Build tools list for template
        List<Map<String, String>> toolsList = new ArrayList<>();
        for (Tool tool : tools) {
            Map<String, String> toolInfo = new HashMap<>();
            toolInfo.put("name", tool.getName());
            toolInfo.put("description", tool.getDescription());
            toolsList.add(toolInfo);
        }

        // Build recent messages list
        List<Map<String, String>> recentMessagesList = new ArrayList<>();
        if (conversation.getMessages() != null && !conversation.getMessages().isEmpty()) {
            int count = 0;
            for (ConversationMessage msg : conversation.getMessages()) {
                if (count++ >= 5) break; // Limit context
                Map<String, String> messageInfo = new HashMap<>();
                messageInfo.put("role", msg.getRole());
                messageInfo.put("content", truncate(msg.getContent(), 200));
                recentMessagesList.add(messageInfo);
            }
        }

        // Prepare variables for template
        Map<String, Object> variables = new HashMap<>();
        variables.put("tools", toolsList);
        variables.put("repositoryUrl", conversation.getRepoUrl() != null ? conversation.getRepoUrl() : "none");
        variables.put("userMessage", userMessage);
        variables.put("hasRecentMessages", !recentMessagesList.isEmpty());
        variables.put("recentMessages", recentMessagesList);

        return promptLibrary.render("autoflow-agent-initial", variables);
    }

    private String buildFollowUpPrompt(String userMessage, Conversation conversation, ToolCall previousCall, ToolResult result) {
        // Build tools list (names only for follow-up)
        List<Map<String, String>> toolsList = new ArrayList<>();
        for (Tool tool : tools) {
            Map<String, String> toolInfo = new HashMap<>();
            toolInfo.put("name", tool.getName());
            toolsList.add(toolInfo);
        }

        // Build recent messages list
        List<Map<String, String>> recentMessagesList = new ArrayList<>();
        if (conversation.getMessages() != null && !conversation.getMessages().isEmpty()) {
            int count = 0;
            for (ConversationMessage msg : conversation.getMessages()) {
                if (count++ >= 5) break;
                Map<String, String> messageInfo = new HashMap<>();
                messageInfo.put("role", msg.getRole());
                messageInfo.put("content", truncate(msg.getContent(), 150));
                recentMessagesList.add(messageInfo);
            }
        }

        // Prepare variables for template
        Map<String, Object> variables = new HashMap<>();
        variables.put("tools", toolsList);
        variables.put("userMessage", userMessage);
        variables.put("hasRecentMessages", !recentMessagesList.isEmpty());
        variables.put("recentMessages", recentMessagesList);
        variables.put("previousTool", previousCall.toolName);
        variables.put("toolSuccess", result.isSuccess());

        if (result.isSuccess()) {
            variables.put("toolData", truncate(String.valueOf(result.getData()), 5000));
        } else {
            variables.put("toolError", result.getMessage());
        }

        return promptLibrary.render("autoflow-agent-followup", variables);
    }

    private String buildFinalResponsePrompt(String userMessage, Conversation conversation, List<String> toolsUsed) {
        // Build recent messages list
        List<Map<String, String>> recentMessagesList = new ArrayList<>();
        if (conversation.getMessages() != null && !conversation.getMessages().isEmpty()) {
            int count = 0;
            for (ConversationMessage msg : conversation.getMessages()) {
                if (count++ >= 10) break; // More context for final response
                Map<String, String> messageInfo = new HashMap<>();
                messageInfo.put("role", msg.getRole());
                messageInfo.put("content", truncate(msg.getContent(), 500));
                recentMessagesList.add(messageInfo);
            }
        }

        // Prepare variables for template
        Map<String, Object> variables = new HashMap<>();
        variables.put("userMessage", userMessage);
        variables.put("repositoryUrl", conversation.getRepoUrl() != null ? conversation.getRepoUrl() : "none");
        variables.put("hasToolsUsed", !toolsUsed.isEmpty());
        variables.put("toolsUsed", String.join(", ", toolsUsed));
        variables.put("hasRecentMessages", !recentMessagesList.isEmpty());
        variables.put("recentMessages", recentMessagesList);

        return promptLibrary.render("autoflow-agent-final", variables);
    }

    private Optional<ToolCall> parseToolCall(String llmResponse) {
        try {
            String json = extractJson(llmResponse);
            if (json == null) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(json);
            if (root.has("tool")) {
                String toolName = root.get("tool").asText();
                Map<String, Object> params = new HashMap<>();
                if (root.has("parameters")) {
                    params = objectMapper.convertValue(root.get("parameters"), Map.class);
                }
                return Optional.of(new ToolCall(toolName, params));
            }
            return Optional.empty();
        } catch (JsonProcessingException e) {
            log.debug("No tool call in response: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String extractFinalResponse(String llmResponse) {
        try {
            String json = extractJson(llmResponse);
            if (json != null) {
                JsonNode root = objectMapper.readTree(json);
                if (root.has("response")) {
                    return root.get("response").asText();
                }
            }
        } catch (JsonProcessingException e) {
            log.debug("Could not parse response JSON");
        }
        return llmResponse;
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private ToolResult executeTool(String toolName, Map<String, Object> parameters, ToolContext context) {
        for (Tool tool : tools) {
            if (tool.getName().equals(toolName)) {
                try {
                    // Run interceptors before execution
                    runBeforeInterceptors(tool, context);

                    // Execute the tool
                    ToolResult result = tool.execute(parameters, context);

                    // Run interceptors after execution
                    runAfterInterceptors(tool, context, result);

                    // Check if we should augment with alternative tools
                    result = augmentWithAlternativesIfNeeded(toolName, parameters, context, result);

                    return result;
                } catch (Exception e) {
                    log.error("Tool {} failed", toolName, e);
                    return ToolResult.failure("Tool execution failed: " + e.getMessage());
                }
            }
        }
        // Tool not found - provide list of valid tools
        String validTools = tools.stream().map(Tool::getName).collect(Collectors.joining(", "));
        log.warn("Unknown tool '{}'. Valid tools: {}", toolName, validTools);
        return ToolResult.failure("Tool '" + toolName + "' does not exist. Valid tools: " + validTools);
    }

    /**
     * Augment tool results with alternative tools if user wants better results.
     */
    private ToolResult augmentWithAlternativesIfNeeded(String toolName, Map<String, Object> parameters,
                                                       ToolContext context, ToolResult primaryResult) {
        // Check if user has indicated they want better/more detailed results
        boolean wantsBetter = context.hasNegativeFeedback();
        int executionCount = context.getToolExecutionCount(toolName);

        if (!wantsBetter || executionCount == 0) {
            // First execution or no negative feedback - return primary result
            return primaryResult;
        }

        // Get alternative tools
        List<String> alternatives = getAlternativeTools(toolName);
        if (alternatives.isEmpty()) {
            return primaryResult;
        }

        log.info("User wants better results - trying alternative tools for {}: {}",
            toolName, alternatives);

        // Execute alternative tools and merge results
        StringBuilder combinedMessage = new StringBuilder(primaryResult.getMessage());
        combinedMessage.append("\n\n--- ALTERNATIVE PERSPECTIVES ---\n");

        for (String altToolName : alternatives) {
            try {
                log.debug("Executing alternative tool: {}", altToolName);
                ToolResult altResult = executeToolDirectly(altToolName, parameters, context);

                if (altResult.isSuccess()) {
                    combinedMessage.append("\n### From ").append(altToolName).append(":\n");
                    combinedMessage.append(altResult.getMessage()).append("\n");
                }
            } catch (Exception e) {
                log.warn("Alternative tool {} failed: {}", altToolName, e.getMessage());
            }
        }

        return ToolResult.success(primaryResult.getData(), combinedMessage.toString());
    }

    /**
     * Execute tool directly without augmentation (to avoid infinite recursion).
     */
    private ToolResult executeToolDirectly(String toolName, Map<String, Object> parameters, ToolContext context) {
        for (Tool tool : tools) {
            if (tool.getName().equals(toolName)) {
                runBeforeInterceptors(tool, context);
                ToolResult result = tool.execute(parameters, context);
                runAfterInterceptors(tool, context, result);
                return result;
            }
        }
        return ToolResult.failure("Tool not found: " + toolName);
    }

    /**
     * Get alternative tools that can provide complementary information.
     */
    private List<String> getAlternativeTools(String toolName) {
        return switch (toolName) {
            case "discover_project" -> List.of("search_code", "dependency_analysis");
            case "search_code" -> List.of("semantic_search", "graph_query");
            case "explain_code" -> List.of("dependency_analysis", "graph_query");
            case "semantic_search" -> List.of("search_code", "graph_query");
            default -> List.of();
        };
    }

    private void runBeforeInterceptors(Tool tool, ToolContext context) {
        for (ToolInterceptor interceptor : interceptors) {
            if (interceptor.appliesTo(tool)) {
                log.debug("Running before interceptor: {} for tool: {}",
                    interceptor.getClass().getSimpleName(), tool.getName());
                interceptor.beforeExecute(tool, context);
            }
        }
    }

    private void runAfterInterceptors(Tool tool, ToolContext context, ToolResult result) {
        for (ToolInterceptor interceptor : interceptors) {
            if (interceptor.appliesTo(tool)) {
                try {
                    interceptor.afterExecute(tool, context, result);
                } catch (Exception e) {
                    log.warn("After interceptor failed: {}", e.getMessage());
                }
            }
        }
    }

    private Conversation getOrCreateConversation(String conversationId, String userId) {
        return conversationService.getConversationWithMessages(conversationId)
            .orElseGet(() -> conversationService.createConversation(conversationId, userId, null));
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private record ToolCall(String toolName, Map<String, Object> parameters) {}
}
