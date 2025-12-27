package com.purchasingpower.autoflow.service;

import com.purchasingpower.autoflow.model.audit.AgentInteraction;
import com.purchasingpower.autoflow.model.audit.LLMRequest;
import com.purchasingpower.autoflow.workflow.state.AgentDecision;
import com.purchasingpower.autoflow.workflow.state.WorkflowState;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service for audit logging and observability.
 * ENTERPRISE: Tracks all system interactions for debugging, compliance, and optimization.
 *
 * Usage:
 * <pre>
 * // Before agent execution
 * String interactionId = auditService.startAgentInteraction(conversationId, agentName, state);
 *
 * // After agent execution
 * auditService.completeAgentInteraction(interactionId, outputState, decision);
 *
 * // Log LLM call
 * String requestId = auditService.logLLMRequest(conversationId, interactionId, provider, model, prompt);
 * auditService.completeLLMRequest(requestId, response, tokens, costUsd);
 * </pre>
 */
public interface AuditLoggingService {

    /**
     * Start tracking an agent interaction.
     *
     * @param conversationId Conversation ID
     * @param workflowId Workflow ID (optional)
     * @param agentName Name of agent being executed
     * @param inputState WorkflowState before agent execution
     * @return interactionId for tracking
     */
    String startAgentInteraction(
            String conversationId,
            String workflowId,
            String agentName,
            WorkflowState inputState
    );

    /**
     * Complete an agent interaction with success.
     *
     * @param interactionId Interaction ID from startAgentInteraction
     * @param outputState WorkflowState after agent execution
     * @param decision AgentDecision produced by agent
     * @param tokensUsed Total tokens used (if applicable)
     */
    void completeAgentInteraction(
            String interactionId,
            WorkflowState outputState,
            AgentDecision decision,
            Long tokensUsed
    );

    /**
     * Mark agent interaction as failed.
     *
     * @param interactionId Interaction ID
     * @param error Exception that caused failure
     */
    void failAgentInteraction(String interactionId, Throwable error);

    /**
     * Start tracking an LLM request.
     *
     * @param conversationId Conversation ID
     * @param interactionId Parent agent interaction ID (optional)
     * @param provider LLM provider (gemini, openai, anthropic)
     * @param model Model name (gemini-1.5-pro, gpt-4, etc.)
     * @param requestType Type of request (text_generation, embedding, chat_completion)
     * @param prompt The prompt sent to LLM
     * @param temperature Temperature parameter
     * @param maxTokens Max tokens parameter
     * @return requestId for tracking
     */
    String startLLMRequest(
            String conversationId,
            String interactionId,
            String provider,
            String model,
            String requestType,
            String prompt,
            Double temperature,
            Integer maxTokens
    );

    /**
     * Complete an LLM request with success.
     *
     * @param requestId Request ID from startLLMRequest
     * @param response LLM response text
     * @param promptTokens Tokens in prompt
     * @param responseTokens Tokens in response
     * @param costUsd Cost in USD
     */
    void completeLLMRequest(
            String requestId,
            String response,
            Long promptTokens,
            Long responseTokens,
            Double costUsd
    );

    /**
     * Mark LLM request as failed.
     *
     * @param requestId Request ID
     * @param httpStatusCode HTTP status code (if applicable)
     * @param error Exception or error message
     */
    void failLLMRequest(String requestId, Integer httpStatusCode, String error);

    /**
     * Log a retrieval operation (Pinecone, Neo4j).
     *
     * @param conversationId Conversation ID
     * @param interactionId Parent agent interaction ID
     * @param retrievalType Type (pinecone_vector, neo4j_graph, hybrid)
     * @param queryText Query text
     * @param resultsFound Number of results
     * @param avgRelevanceScore Average relevance score
     * @param durationMs Time taken in milliseconds
     */
    void logRetrieval(
            String conversationId,
            String interactionId,
            String retrievalType,
            String queryText,
            Integer resultsFound,
            Double avgRelevanceScore,
            Long durationMs
    );

    /**
     * Get agent interaction metrics for a conversation.
     *
     * @param conversationId Conversation ID
     * @return List of agent interactions
     */
    java.util.List<AgentInteraction> getConversationInteractions(String conversationId);

    /**
     * Get LLM request metrics for a conversation.
     *
     * @param conversationId Conversation ID
     * @return List of LLM requests
     */
    java.util.List<LLMRequest> getConversationLLMRequests(String conversationId);

    /**
     * Get total cost for a conversation.
     *
     * @param conversationId Conversation ID
     * @return Total cost in USD
     */
    Double getConversationTotalCost(String conversationId);

    /**
     * Get aggregate metrics for debugging/dashboards.
     *
     * @param fromDate Start date (optional)
     * @param toDate End date (optional)
     * @return Aggregate metrics
     */
    Map<String, Object> getAggregateMetrics(LocalDateTime fromDate, LocalDateTime toDate);
}
