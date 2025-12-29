package com.purchasingpower.autoflow.service.impl;

import com.purchasingpower.autoflow.model.workflow.WorkflowStateEntity;
import com.purchasingpower.autoflow.repository.WorkflowStateRepository;
import com.purchasingpower.autoflow.service.ConversationService;
import com.purchasingpower.autoflow.service.WorkflowExecutionService;
import com.purchasingpower.autoflow.workflow.AutoFlowWorkflow;
import com.purchasingpower.autoflow.workflow.state.AgentDecision;
import com.purchasingpower.autoflow.workflow.state.WorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * FIXED: Uses direct executor injection instead of @Async.
 * Submits tasks directly to thread pool - no AOP, no proxies, no issues!
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {

    private final AutoFlowWorkflow autoFlowWorkflow;
    private final WorkflowStateRepository stateRepository;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    // Direct injection of the async executor - much simpler than @Async!
    @Qualifier("workflowExecutor")
    private final Executor workflowExecutor;

    private final ConcurrentHashMap<String, WorkflowState> activeWorkflows = new ConcurrentHashMap<>();

    @Override
    public WorkflowState startWorkflow(WorkflowState initialState) {
        log.info("Starting new workflow");

        try {
            // Generate conversation ID if not present
            if (initialState.getConversationId() == null) {
                // FIX: Create new state with ID using Map
                Map<String, Object> data = new HashMap<>(initialState.toMap());
                data.put("conversationId", UUID.randomUUID().toString());
                initialState = WorkflowState.fromMap(data);
            }

            // FIX: Create new state with RUNNING status
            Map<String, Object> data = new HashMap<>(initialState.toMap());
            data.put("workflowStatus", "RUNNING");
            WorkflowState runningState = WorkflowState.fromMap(data);

            // Save initial state
            saveWorkflowState(runningState);

            // Cache it
            activeWorkflows.put(runningState.getConversationId(), runningState);

            // Submit workflow execution to async thread pool - returns immediately!
            log.info("🚀 Submitting workflow to async executor...");
            workflowExecutor.execute(() -> executeWorkflow(runningState));

            return runningState;

        } catch (Exception e) {
            log.error("Failed to start workflow", e);
            throw new RuntimeException("Failed to start workflow", e);
        }
    }

    @Override
    public WorkflowState resumeWorkflow(WorkflowState state) {
        log.info("Resuming workflow: {}", state.getConversationId());

        try {
            // FIX: Create new state with RUNNING status (don't modify original)
            Map<String, Object> data = new HashMap<>(state.toMap());
            data.put("workflowStatus", "RUNNING");
            WorkflowState runningState = WorkflowState.fromMap(data);

            // Save state
            saveWorkflowState(runningState);

            // Update cache
            activeWorkflows.put(runningState.getConversationId(), runningState);

            // Resume workflow execution in async thread pool - returns immediately!
            log.info("🚀 Resuming workflow in async executor...");
            workflowExecutor.execute(() -> executeWorkflow(runningState));

            return runningState;

        } catch (Exception e) {
            log.error("Failed to resume workflow", e);
            throw new RuntimeException("Failed to resume workflow", e);
        }
    }

    @Override
    public WorkflowState getWorkflowState(String conversationId) {
        log.debug("Getting workflow state: {}", conversationId);

        // ✅ THREAD SAFETY FIX: Use computeIfAbsent for atomic cache population
        // ─────────────────────────────────────────────────────────────────────────
        // OLD CODE (RACE CONDITION):
        //   WorkflowState cached = activeWorkflows.get(conversationId);
        //   if (cached != null) return cached;
        //   // Load from database
        //   WorkflowState loaded = loadFromDb();
        //   activeWorkflows.put(conversationId, loaded);  // ← Race condition!
        //
        // Problem:
        //   Thread A: get() returns null
        //   Thread B: get() returns null (same time!)
        //   Thread A: Loads from database
        //   Thread B: Loads from database (duplicate load!)
        //   Thread A: put() into cache
        //   Thread B: put() into cache (overwrites A's value!)
        //
        // FIX: computeIfAbsent is atomic - only ONE thread loads from database
        // ─────────────────────────────────────────────────────────────────────────
        return activeWorkflows.computeIfAbsent(conversationId, id -> {
            log.debug("Cache miss, loading workflow from database: {}", id);

            try {
                WorkflowStateEntity entity = stateRepository.findByConversationId(id)
                        .orElse(null);

                if (entity == null) {
                    log.debug("Workflow not found in database: {}", id);
                    return null;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> stateMap = objectMapper.readValue(
                        entity.getStateJson(),
                        Map.class
                );

                WorkflowState loaded = WorkflowState.fromMap(stateMap);
                log.debug("✅ Loaded workflow from database: {}", id);
                return loaded;

            } catch (Exception e) {
                log.error("Failed to load workflow state: {}", id, e);
                return null;
            }
        });
    }

    @Override
    public void cancelWorkflow(String conversationId) {
        log.info("Cancelling workflow: {}", conversationId);

        try {
            WorkflowState state = getWorkflowState(conversationId);
            if (state != null) {
                // FIX: Create new state with CANCELLED status
                Map<String, Object> data = new HashMap<>(state.toMap());
                data.put("workflowStatus", "CANCELLED");
                WorkflowState cancelledState = WorkflowState.fromMap(data);

                saveWorkflowState(cancelledState);
                activeWorkflows.remove(conversationId);
            }

        } catch (Exception e) {
            log.error("Failed to cancel workflow", e);
            throw new RuntimeException("Failed to cancel workflow", e);
        }
    }

    /**
     * Execute workflow in background thread.
     * Called via workflowExecutor.execute() - runs in thread pool.
     */
    private void executeWorkflow(WorkflowState state) {
        try {
            log.info("🚀 [ASYNC THREAD {}] Executing workflow: {}",
                    Thread.currentThread().getName(), state.getConversationId());

            // Execute the LangGraph4j workflow
            WorkflowState result = autoFlowWorkflow.execute(state);

            // Save final state
            saveWorkflowState(result);

            // ✅ FIX CRITICAL #2: Remove completed workflows from cache to prevent memory leak
            // BEFORE: activeWorkflows.put() → workflows never removed → OOM after 10k workflows
            // AFTER: Remove from cache when workflow terminates (COMPLETED, FAILED, CANCELLED)
            String finalStatus = result.getWorkflowStatus();
            if ("COMPLETED".equals(finalStatus) || "FAILED".equals(finalStatus) ||
                "CANCELLED".equals(finalStatus)) {
                activeWorkflows.remove(result.getConversationId());
                log.debug("Removed completed workflow from cache: {}", result.getConversationId());
            } else {
                // Only keep in cache if still RUNNING or PAUSED
                activeWorkflows.put(result.getConversationId(), result);
            }

            log.info("Workflow execution completed: {} - status: {}",
                    result.getConversationId(),
                    result.getWorkflowStatus());

        } catch (Exception e) {
            log.error("Workflow execution failed: {}", state.getConversationId(), e);

            // FIX: Create new FAILED state (don't modify original)
            Map<String, Object> data = new HashMap<>(state.toMap());
            data.put("workflowStatus", "FAILED");
            data.put("lastAgentDecision", AgentDecision.error(e.getMessage()));
            WorkflowState failedState = WorkflowState.fromMap(data);

            saveWorkflowState(failedState);
            activeWorkflows.remove(state.getConversationId());
        }
    }

    private void saveWorkflowState(WorkflowState state) {
        try {
            // ================================================================
            // CRITICAL FIX: Persist conversation history to normalized tables
            // ================================================================
            // This was the root cause of empty CONVERSATION_MESSAGES and
            // CONVERSATION_CONTEXT tables. We were only saving to WORKFLOW_STATES
            // as JSON, but never persisting to normalized tables.
            conversationService.saveConversationFromWorkflowState(state);

            // ================================================================
            // Also save to WORKFLOW_STATES (JSON snapshot for compatibility)
            // ================================================================
            // Serialize state to JSON
            String stateJson = objectMapper.writeValueAsString(state);

            // Find existing entity or create new
            WorkflowStateEntity entity = stateRepository
                    .findByConversationId(state.getConversationId())
                    .orElse(null);

            if (entity == null) {
                entity = new WorkflowStateEntity();
            }

            // Update entity
            entity.setConversationId(state.getConversationId());
            entity.setUserId(state.getUserId());
            entity.setStatus(state.getWorkflowStatus());
            entity.setCurrentAgent(state.getCurrentAgent());
            entity.setStateJson(stateJson);

            // Save
            stateRepository.save(entity);

            log.debug("Saved workflow state: {}", state.getConversationId());

        } catch (Exception e) {
            log.error("Failed to save workflow state", e);
            // ✅ FIX CRITICAL #1: Re-throw exception to prevent data loss
            // If save fails, workflow MUST NOT continue with unsaved state
            throw new RuntimeException("CRITICAL: Failed to persist workflow state - cannot continue", e);
        }
    }
}