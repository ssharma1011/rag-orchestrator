package com.purchasingpower.autoflow.service.impl;

import com.purchasingpower.autoflow.model.metrics.AgentExecution;
import com.purchasingpower.autoflow.repository.AgentExecutionRepository;
import com.purchasingpower.autoflow.service.AgentMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of AgentMetricsService.
 *
 * Provides agent execution tracking and performance analytics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMetricsServiceImpl implements AgentMetricsService {

    private final AgentExecutionRepository executionRepo;

    @Override
    @Transactional
    public void recordExecution(AgentExecution execution) {
        try {
            executionRepo.save(execution);
            log.debug("Recorded agent execution: {} - {} ({}ms)",
                    execution.getAgentName(),
                    execution.getStatus(),
                    execution.getLatencyMs());
        } catch (Exception e) {
            log.error("Failed to record agent execution", e);
        }
    }

    @Override
    public List<AgentExecution> getExecutionsForConversation(String conversationId) {
        return executionRepo.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Override
    public Map<String, Object> getAgentPerformanceStats(LocalDateTime since) {
        try {
            List<Object[]> results = executionRepo.getAgentPerformanceStats(since);

            Map<String, Object> stats = new HashMap<>();
            for (Object[] row : results) {
                String agentName = (String) row[0];
                Map<String, Object> agentStats = new HashMap<>();
                agentStats.put("executionCount", row[1]);
                agentStats.put("avgLatency", row[2]);
                agentStats.put("maxLatency", row[3]);
                agentStats.put("minLatency", row[4]);
                agentStats.put("avgTokens", row[5]);
                agentStats.put("successCount", row[6]);
                agentStats.put("failureCount", row[7]);

                stats.put(agentName, agentStats);
            }

            return stats;
        } catch (Exception e) {
            log.error("Failed to get agent performance stats", e);
            return new HashMap<>();
        }
    }

    @Override
    public List<AgentExecution> getSlowestExecutions(int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        return executionRepo.findSlowestExecutions();
    }

    @Override
    public List<AgentExecution> getFailedExecutions(int limit) {
        List<AgentExecution> failed = executionRepo.findByStatusOrderByCreatedAtDesc("FAILED");
        return failed.size() > limit ? failed.subList(0, limit) : failed;
    }

    @Override
    public Map<String, Map<String, Long>> getTokenUsageByAgent(LocalDateTime since) {
        try {
            List<Object[]> results = executionRepo.getTokenUsageByAgent(since);

            Map<String, Map<String, Long>> tokenUsage = new HashMap<>();
            for (Object[] row : results) {
                String agentName = (String) row[0];
                Map<String, Long> usage = new HashMap<>();
                usage.put("inputTokens", (Long) row[1]);
                usage.put("outputTokens", (Long) row[2]);
                usage.put("totalTokens", (Long) row[1] + (Long) row[2]);

                tokenUsage.put(agentName, usage);
            }

            return tokenUsage;
        } catch (Exception e) {
            log.error("Failed to get token usage by agent", e);
            return new HashMap<>();
        }
    }
}
