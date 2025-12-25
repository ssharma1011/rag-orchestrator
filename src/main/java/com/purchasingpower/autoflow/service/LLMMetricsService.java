package com.purchasingpower.autoflow.service;

import com.purchasingpower.autoflow.model.metrics.AgentPerformanceStats;
import com.purchasingpower.autoflow.model.metrics.LLMCallMetrics;
import com.purchasingpower.autoflow.model.metrics.LLMCallMetricsEntity;
import com.purchasingpower.autoflow.repository.LLMCallMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for tracking LLM call metrics and generating analytics.
 *
 * Provides:
 * - Cost tracking (per conversation, per agent, per time period)
 * - Performance monitoring (latency, throughput)
 * - Quality assessment (RAGAS metrics)
 * - Failure analysis (retry rates, error patterns)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMMetricsService {

    private final LLMCallMetricsRepository metricsRepository;

    /**
     * Record an LLM call for metrics tracking.
     * Call this from GeminiClient after every API call.
     */
    public void recordCall(LLMCallMetrics metrics) {
        try {
            // Calculate derived metrics
            metrics.setEstimatedCost(metrics.calculateCost());
            metrics.setTokensPerSecond(metrics.calculateTokensPerSecond());
            metrics.setTotalTokens(metrics.getInputTokens() + metrics.getOutputTokens());

            // Convert to entity and save
            LLMCallMetricsEntity entity = toEntity(metrics);
            metricsRepository.save(entity);

            log.debug("Recorded LLM metrics: agent={}, tokens={}, cost=${}, latency={}ms",
                    metrics.getAgentName(),
                    metrics.getTotalTokens(),
                    String.format("%.4f", metrics.getEstimatedCost()),
                    metrics.getLatencyMs());

        } catch (Exception e) {
            log.error("Failed to record LLM metrics", e);
            // Don't throw - metrics recording should never break the workflow
        }
    }

    /**
     * Get all metrics for a conversation
     */
    public List<LLMCallMetrics> getConversationMetrics(String conversationId) {
        return metricsRepository.findByConversationIdOrderByTimestampDesc(conversationId)
                .stream()
                .map(this::toModel)
                .toList();
    }

    /**
     * Get total cost for a conversation
     */
    public double getConversationCost(String conversationId) {
        Double cost = metricsRepository.calculateTotalCostForConversation(conversationId);
        return cost != null ? cost : 0.0;
    }

    /**
     * Get agent performance statistics
     */
    public AgentPerformanceStats getAgentStats(String agentName) {
        Long totalTokens = metricsRepository.calculateTotalTokensByAgent(agentName);
        Double avgLatency = metricsRepository.getAverageLatencyByAgent(agentName);
        Double successRate = metricsRepository.getSuccessRateByAgent(agentName);
        Object[] ragasScores = metricsRepository.getAverageRAGASScoresByAgent(agentName);

        return AgentPerformanceStats.builder()
                .agentName(agentName)
                .totalTokensUsed(totalTokens != null ? totalTokens : 0L)
                .averageLatencyMs(avgLatency != null ? avgLatency : 0.0)
                .successRate(successRate != null ? successRate : 0.0)
                .avgContextRelevance(ragasScores != null && ragasScores[0] != null ?
                        (Double) ragasScores[0] : null)
                .avgAnswerRelevance(ragasScores != null && ragasScores[1] != null ?
                        (Double) ragasScores[1] : null)
                .avgFaithfulness(ragasScores != null && ragasScores[2] != null ?
                        (Double) ragasScores[2] : null)
                .avgResponseQuality(ragasScores != null && ragasScores[3] != null ?
                        (Double) ragasScores[3] : null)
                .build();
    }

    /**
     * Get cost for a time period
     */
    public double getCostInPeriod(LocalDateTime start, LocalDateTime end) {
        Double cost = metricsRepository.calculateCostInPeriod(start, end);
        return cost != null ? cost : 0.0;
    }

    /**
     * Get failed calls for debugging
     */
    public List<LLMCallMetrics> getFailedCalls() {
        return metricsRepository.findBySuccessFalseOrderByTimestampDesc()
                .stream()
                .map(this::toModel)
                .toList();
    }

    /**
     * Get calls with high retry count (indicating rate limit or server issues)
     */
    public List<LLMCallMetrics> getProblematicCalls(int retryThreshold) {
        return metricsRepository.findHighRetryCountCalls(retryThreshold)
                .stream()
                .map(this::toModel)
                .toList();
    }

    // ================================================================
    // CONVERSION METHODS
    // ================================================================

    private LLMCallMetricsEntity toEntity(LLMCallMetrics model) {
        LLMCallMetricsEntity entity = new LLMCallMetricsEntity();

        entity.setCallId(model.getCallId() != null ? model.getCallId() : UUID.randomUUID().toString());
        entity.setAgentName(model.getAgentName());
        entity.setConversationId(model.getConversationId());
        entity.setTimestamp(model.getTimestamp() != null ? model.getTimestamp() : LocalDateTime.now());

        entity.setModel(model.getModel());
        entity.setPrompt(model.getPrompt());
        entity.setPromptLength(model.getPromptLength());
        entity.setTemperature(model.getTemperature());
        entity.setMaxTokens(model.getMaxTokens());

        entity.setResponse(model.getResponse());
        entity.setResponseLength(model.getResponseLength());
        entity.setSuccess(model.isSuccess());
        entity.setErrorMessage(model.getErrorMessage());

        entity.setTimeToFirstToken(model.getTimeToFirstToken());
        entity.setLatencyMs(model.getLatencyMs());
        entity.setTokensPerSecond(model.getTokensPerSecond());

        entity.setInputTokens(model.getInputTokens());
        entity.setOutputTokens(model.getOutputTokens());
        entity.setTotalTokens(model.getTotalTokens());
        entity.setEstimatedCost(model.getEstimatedCost());

        entity.setContextRelevanceScore(model.getContextRelevanceScore());
        entity.setAnswerRelevanceScore(model.getAnswerRelevanceScore());
        entity.setFaithfulnessScore(model.getFaithfulnessScore());
        entity.setResponseQualityScore(model.getResponseQualityScore());

        entity.setRetryCount(model.getRetryCount());
       // entity.setIsRetry(model.isRetry());
        entity.setRetry(model.isRetry());
        entity.setHttpStatusCode(model.getHttpStatusCode());

        return entity;
    }

    private LLMCallMetrics toModel(LLMCallMetricsEntity entity) {
        return LLMCallMetrics.builder()
                .callId(entity.getCallId())
                .agentName(entity.getAgentName())
                .conversationId(entity.getConversationId())
                .timestamp(entity.getTimestamp())
                .model(entity.getModel())
                .prompt(entity.getPrompt())
                .promptLength(entity.getPromptLength())
                .temperature(entity.getTemperature())
                .maxTokens(entity.getMaxTokens())
                .response(entity.getResponse())
                .responseLength(entity.getResponseLength())
                .success(entity.isSuccess())
                .errorMessage(entity.getErrorMessage())
                .timeToFirstToken(entity.getTimeToFirstToken())
                .latencyMs(entity.getLatencyMs())
                .tokensPerSecond(entity.getTokensPerSecond())
                .inputTokens(entity.getInputTokens())
                .outputTokens(entity.getOutputTokens())
                .totalTokens(entity.getTotalTokens())
                .estimatedCost(entity.getEstimatedCost())
                .contextRelevanceScore(entity.getContextRelevanceScore())
                .answerRelevanceScore(entity.getAnswerRelevanceScore())
                .faithfulnessScore(entity.getFaithfulnessScore())
                .responseQualityScore(entity.getResponseQualityScore())
                .retryCount(entity.getRetryCount())
                .isRetry(entity.isRetry())
                .httpStatusCode(entity.getHttpStatusCode())
                .build();
    }

}