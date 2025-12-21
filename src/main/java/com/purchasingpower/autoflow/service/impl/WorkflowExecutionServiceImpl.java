package com.purchasingpower.autoflow.service.impl;

import com.purchasingpower.autoflow.model.WorkflowStateEntity;
import com.purchasingpower.autoflow.repository.WorkflowStateRepository;
import com.purchasingpower.autoflow.service.WorkflowExecutionService;
import com.purchasingpower.autoflow.workflow.AutoFlowWorkflow;
import com.purchasingpower.autoflow.workflow.state.WorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of WorkflowExecutionService.
 *
 * Manages workflow execution using:
 * - AutoFlowWorkflow (LangGraph4j orchestration)
 * - WorkflowStateRepository (persistence)
 * - In-memory cache for active workflows
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {

    private final AutoFlowWorkflow autoFlowWorkflow;
    private final WorkflowStateRepository stateRepository;
    private final ObjectMapper objectMapper;

    // In-memory cache of active workflows
    private final ConcurrentHashMap<String, WorkflowState> activeWorkflows = new ConcurrentHashMap<>();

    @Override
    public WorkflowState startWorkflow(WorkflowState initialState) {
        log.info("Starting new workflow");

        try {
            // Generate conversation ID if not present
            if (initialState.getConversationId() == null) {
                String conversationId = UUID.randomUUID().toString();
                initialState.setConversationId(conversationId);
            }

            // Set initial status
            initialState.setWorkflowStatus("RUNNING");

            // Save initial state
            saveWorkflowState(initialState);

            // Cache it
            activeWorkflows.put(initialState.getConversationId(), initialState);

            // Execute workflow asynchronously
            executeWorkflowAsync(initialState);

            return initialState;

        } catch (Exception e) {
            log.error("Failed to start workflow", e);
            throw new RuntimeException("Failed to start workflow", e);
        }
    }

    @Override
    public WorkflowState resumeWorkflow(WorkflowState state) {
        log.info("Resuming workflow: {}", state.getConversationId());

        try {
            // Update status to running
            state.setWorkflowStatus("RUNNING");

            // Save state
            saveWorkflowState(state);

            // Update cache
            activeWorkflows.put(state.getConversationId(), state);

            // Continue execution asynchronously
            executeWorkflowAsync(state);

            return state;

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

            // Deserialize state from JSON
            WorkflowState state = objectMapper.readValue(
                    entity.getStateJson(),
                    WorkflowState.class
            );

            return state;

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
                state.setWorkflowStatus("CANCELLED");
                saveWorkflowState(state);
                activeWorkflows.remove(conversationId);
            }

        } catch (Exception e) {
            log.error("Failed to cancel workflow", e);
            throw new RuntimeException("Failed to cancel workflow", e);
        }
    }

    /**
     * Execute workflow asynchronously.
     */
    @Async
    protected void executeWorkflowAsync(WorkflowState state) {
        try {
            log.info("Executing workflow async: {}", state.getConversationId());

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

            // Mark as failed
            state.setWorkflowStatus("FAILED");
            saveWorkflowState(state);
            activeWorkflows.remove(state.getConversationId());
        }
    }

    /**
     * Save workflow state to database.
     */
    private void saveWorkflowState(WorkflowState state) {
        try {
            // Serialize state to JSON
            String stateJson = objectMapper.writeValueAsString(state);

            // Find existing entity or create new
            WorkflowStateEntity entity = stateRepository
                    .findByConversationId(state.getConversationId())
                    .orElse(new WorkflowStateEntity());

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
