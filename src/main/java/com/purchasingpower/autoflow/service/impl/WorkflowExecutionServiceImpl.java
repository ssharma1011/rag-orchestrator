package com.purchasingpower.autoflow.service.impl;

import com.purchasingpower.autoflow.model.workflow.WorkflowStateEntity;
import com.purchasingpower.autoflow.repository.WorkflowStateRepository;
import com.purchasingpower.autoflow.service.WorkflowExecutionService;
import com.purchasingpower.autoflow.workflow.AutoFlowWorkflow;
import com.purchasingpower.autoflow.workflow.state.AgentDecision;
import com.purchasingpower.autoflow.workflow.state.WorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FIXED: Uses self-injection to make @Async work correctly.
 * Spring AOP requires calling through proxy for @Async to work.
 */
@Slf4j
@Service
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {

    private final AutoFlowWorkflow autoFlowWorkflow;
    private final WorkflowStateRepository stateRepository;
    private final ObjectMapper objectMapper;

    // Self-injection to get the proxied version for @Async
    @Autowired
    private WorkflowExecutionService self;

    private final ConcurrentHashMap<String, WorkflowState> activeWorkflows = new ConcurrentHashMap<>();

    public WorkflowExecutionServiceImpl(
            AutoFlowWorkflow autoFlowWorkflow,
            WorkflowStateRepository stateRepository,
            ObjectMapper objectMapper) {
        this.autoFlowWorkflow = autoFlowWorkflow;
        this.stateRepository = stateRepository;
        this.objectMapper = objectMapper;
    }

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

            // Execute workflow asynchronously using self (proxy) for @Async to work
            log.info("🚀 Calling async execution through proxy...");
            self.executeWorkflowAsync(runningState);

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

            // Continue execution asynchronously using self (proxy) for @Async to work
            log.info("🚀 Resuming async execution through proxy...");
            self.executeWorkflowAsync(runningState);

            return runningState;

        } catch (Exception e) {
            log.error("Failed to resume workflow", e);
            throw new RuntimeException("Failed to resume workflow", e);
        }
    }

    @Override
    public WorkflowState getWorkflowState(String conversationId) {
        log.debug("Getting workflow state: {}", conversationId);

        // Check cache first
        WorkflowState cached = activeWorkflows.get(conversationId);
        if (cached != null) {
            return cached;
        }

        // Load from database
        try {
            WorkflowStateEntity entity = stateRepository.findByConversationId(conversationId)
                    .orElse(null);

            if (entity == null) {
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> stateMap = objectMapper.readValue(
                    entity.getStateJson(),
                    Map.class
            );

            return WorkflowState.fromMap(stateMap);

        } catch (Exception e) {
            log.error("Failed to load workflow state", e);
            return null;
        }
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

    @Async("workflowExecutor")
    public void executeWorkflowAsync(WorkflowState state) {
        try {
            log.info("🚀 [ASYNC THREAD] Executing workflow async: {}", state.getConversationId());

            // Execute the LangGraph4j workflow
            WorkflowState result = autoFlowWorkflow.execute(state);

            // Save final state
            saveWorkflowState(result);

            // Update cache
            activeWorkflows.put(result.getConversationId(), result);

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
        }
    }
}