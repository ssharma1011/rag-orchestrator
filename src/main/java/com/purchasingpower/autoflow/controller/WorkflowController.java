package com.purchasingpower.autoflow.controller;

import com.purchasingpower.autoflow.model.dto.WorkflowHistoryResponse;
import com.purchasingpower.autoflow.model.dto.*;
import com.purchasingpower.autoflow.service.WorkflowExecutionService;
import com.purchasingpower.autoflow.workflow.state.ChatMessage;
import com.purchasingpower.autoflow.workflow.state.WorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for workflow operations.
 *
 * IMPORTANT: Never calls setters on WorkflowState after LangGraph4j processing.
 * Uses Map manipulation to avoid UnsupportedOperationException.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowExecutionService workflowService;

    /**
     * Start a new workflow.
     *
     * Returns immediately with HTTP 202 Accepted while workflow executes asynchronously.
     * Use /status endpoint to check progress.
     */
    @PostMapping("/start")
    public ResponseEntity<WorkflowResponse> startWorkflow(@RequestBody WorkflowRequest request) {
        try {
            log.info("Starting workflow for user: {}", request.getUserId());

            WorkflowState initialState = WorkflowState.builder()
                    .requirement(request.getRequirement())
                    .repoUrl(request.getRepoUrl())
                    .baseBranch(request.getBaseBranch() != null ? request.getBaseBranch() : "main")
                    .jiraUrl(request.getJiraUrl())
                    .logsPasted(request.getLogsPasted())
                    .userId(request.getUserId())
                    .build();

            // Start workflow asynchronously - returns immediately
            WorkflowState runningState = workflowService.startWorkflow(initialState);

            // Return 202 Accepted with conversationId
            return ResponseEntity.accepted()
                    .body(WorkflowResponse.fromState(runningState));

        } catch (Exception e) {
            log.error("Failed to start workflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(WorkflowResponse.error(e.getMessage()));
        }
    }

    /**
     * Respond to agent question (resume paused workflow).
     *
     * CRITICAL: Does NOT call state.addChatMessage() - creates new state instead.
     * Returns immediately with HTTP 202 Accepted while workflow continues asynchronously.
     */
    @PostMapping("/{conversationId}/respond")
    public ResponseEntity<WorkflowResponse> respondToPrompt(
            @PathVariable String conversationId,
            @RequestBody UserResponse userResponse) {
        try {
            WorkflowState state = workflowService.getWorkflowState(conversationId);
            if (state == null) return ResponseEntity.notFound().build();

            Map<String, Object> data = new HashMap<>(state.toMap());

            // Get current history
            @SuppressWarnings("unchecked")
            List<ChatMessage> history = (List<ChatMessage>) data.getOrDefault("conversationHistory", new ArrayList<>());
            List<ChatMessage> newHistory = new ArrayList<>(history);

            // Add user response
            ChatMessage userMessage = new ChatMessage();
            userMessage.setRole("user");
            userMessage.setContent(userResponse.getResponse());
            userMessage.setTimestamp(LocalDateTime.now());
            newHistory.add(userMessage);

            // Add additional context if provided
            if (userResponse.getAdditionalContext() != null) {
                ChatMessage contextMessage = new ChatMessage();
                contextMessage.setRole("user");
                contextMessage.setContent("Context: " + userResponse.getAdditionalContext());
                contextMessage.setTimestamp(LocalDateTime.now());
                newHistory.add(contextMessage);
            }

            // Update data with new history
            data.put("conversationHistory", newHistory);

            // Create new state (don't modify original)
            WorkflowState updatedState = WorkflowState.fromMap(data);

            // Resume workflow asynchronously - returns immediately
            WorkflowState runningState = workflowService.resumeWorkflow(updatedState);

            // Return 202 Accepted
            return ResponseEntity.accepted()
                    .body(WorkflowResponse.fromState(runningState));

        } catch (Exception e) {
            log.error("Failed to resume workflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(WorkflowResponse.error(e.getMessage()));
        }
    }

    /**
     * Get current workflow status.
     */
    @GetMapping("/{conversationId}/status")
    public ResponseEntity<WorkflowResponse> getStatus(@PathVariable String conversationId) {
        try {
            WorkflowState state = workflowService.getWorkflowState(conversationId);
            if (state == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(WorkflowResponse.fromState(state));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(WorkflowResponse.error(e.getMessage()));
        }
    }

    /**
     * Get conversation history.
     */
    @GetMapping("/{conversationId}/history")
    public ResponseEntity<WorkflowHistoryResponse> getHistory(@PathVariable String conversationId) {
        try {
            WorkflowState state = workflowService.getWorkflowState(conversationId);
            if (state == null) return ResponseEntity.notFound().build();

            WorkflowHistoryResponse history = WorkflowHistoryResponse.builder()
                    .conversationId(conversationId)
                    .messages(state.getConversationHistory())
                    .status(state.getWorkflowStatus())
                    .build();

            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Failed to get conversation history", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}