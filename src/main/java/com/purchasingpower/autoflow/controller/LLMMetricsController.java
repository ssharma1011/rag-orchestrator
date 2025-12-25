package com.purchasingpower.autoflow.controller;

import com.purchasingpower.autoflow.model.metrics.AgentPerformanceStats;
import com.purchasingpower.autoflow.model.metrics.LLMCallMetrics;
import com.purchasingpower.autoflow.service.LLMMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for LLM metrics and cost analysis.
 * 
 * Endpoints:
 * - GET /api/v1/metrics/conversation/{id} - All metrics for a conversation
 * - GET /api/v1/metrics/agent/{name} - Performance stats for an agent
 * - GET /api/v1/metrics/cost - Cost analysis
 * - GET /api/v1/metrics/failures - Failed LLM calls
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
public class LLMMetricsController {

    private final LLMMetricsService metricsService;

    /**
     * Get all LLM calls for a specific conversation
     */
    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<ConversationMetricsResponse> getConversationMetrics(
            @PathVariable String conversationId) {
        
        List<LLMCallMetrics> metrics = metricsService.getConversationMetrics(conversationId);
        double totalCost = metricsService.getConversationCost(conversationId);
        
        long totalTokens = metrics.stream()
                .mapToLong(LLMCallMetrics::getTotalTokens)
                .sum();
        
        double avgLatency = metrics.stream()
                .mapToLong(LLMCallMetrics::getLatencyMs)
                .average()
                .orElse(0.0);
        
        return ResponseEntity.ok(ConversationMetricsResponse.builder()
                .conversationId(conversationId)
                .totalCalls(metrics.size())
                .totalTokens(totalTokens)
                .totalCost(totalCost)
                .averageLatencyMs(avgLatency)
                .calls(metrics)
                .build());
    }

    /**
     * Get performance statistics for a specific agent
     */
    @GetMapping("/agent/{agentName}")
    public ResponseEntity<AgentPerformanceStats> getAgentStats(
            @PathVariable String agentName) {
        
        AgentPerformanceStats stats = metricsService.getAgentStats(agentName);
        return ResponseEntity.ok(stats);
    }

    /**
     * Get cost analysis for a time period
     */
    @GetMapping("/cost")
    public ResponseEntity<CostAnalysisResponse> getCostAnalysis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        
        double totalCost = metricsService.getCostInPeriod(start, end);
        
        return ResponseEntity.ok(CostAnalysisResponse.builder()
                .startTime(start)
                .endTime(end)
                .totalCost(totalCost)
                .build());
    }

    /**
     * Get failed LLM calls for debugging
     */
    @GetMapping("/failures")
    public ResponseEntity<List<LLMCallMetrics>> getFailures() {
        List<LLMCallMetrics> failures = metricsService.getFailedCalls();
        return ResponseEntity.ok(failures);
    }

    /**
     * Get calls with high retry count (indicating problems)
     */
    @GetMapping("/problematic")
    public ResponseEntity<List<LLMCallMetrics>> getProblematicCalls(
            @RequestParam(defaultValue = "2") int retryThreshold) {
        
        List<LLMCallMetrics> problematic = metricsService.getProblematicCalls(retryThreshold);
        return ResponseEntity.ok(problematic);
    }

    /**
     * Get overall system metrics dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        // Get metrics for last 24 hours
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusDays(1);
        
        double cost24h = metricsService.getCostInPeriod(yesterday, now);
        
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("cost_last_24h", cost24h);
        dashboard.put("timestamp", now);
        
        // Add more dashboard stats as needed
        
        return ResponseEntity.ok(dashboard);
    }

    // ================================================================
    // RESPONSE DTOs
    // ================================================================

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ConversationMetricsResponse {
        private String conversationId;
        private int totalCalls;
        private long totalTokens;
        private double totalCost;
        private double averageLatencyMs;
        private List<LLMCallMetrics> calls;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CostAnalysisResponse {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private double totalCost;
    }
}
