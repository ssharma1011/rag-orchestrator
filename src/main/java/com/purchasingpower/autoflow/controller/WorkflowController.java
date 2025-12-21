package com.purchasingpower.autoflow.controller;

import com.purchasingpower.autoflow.service.WorkflowExecutionService;
import com.purchasingpower.autoflow.workflow.state.WorkflowState;
import com.purchasingpower.autoflow.workflow.state.FileUpload;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST API for AutoFlow workflow orchestration.
 * 
 * Endpoints:
 * 1. POST /api/v1/workflows/start - Start new workflow
 * 2. GET /api/v1/workflows/{id}/status - Check workflow status
 * 3. POST /api/v1/workflows/{id}/respond - Respond to ASK_DEV prompts
 * 4. GET /api/v1/workflows/{id}/history - Get conversation history
 * 5. DELETE /api/v1/workflows/{id} - Cancel workflow
 * 
 * @author AutoFlow Team
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Configure properly for production
public class WorkflowController {

    private final WorkflowExecutionService workflowService;

    /**
     * Start a new workflow.
     * 
     * Request body:
     * {
     *   "requirement": "Add retry logic to PaymentService",
     *   "targetClass": "PaymentService",
     *   "repoUrl": "https://github.com/org/repo",
     *   "baseBranch": "main",
     *   "jiraUrl": "https://jira.com/PROJ-123",
     *   "logsPasted": "ERROR: NullPointerException..."
     * }
     * 
     * Response:
     * {
     *   "conversationId": "uuid",
     *   "status": "RUNNING",
     *   "currentAgent": "requirement_analyzer",
     *   "message": "Analyzing your requirement..."
     * }
     */
    @PostMapping("/start")
    public ResponseEntity<WorkflowResponse> startWorkflow(
            @RequestBody StartWorkflowRequest request) {
        
        log.info("📥 Starting workflow: requirement={}, targetClass={}", 
                request.getRequirement(), request.getTargetClass());

        try {
            // Validate required fields
            if (request.getRequirement() == null || request.getRequirement().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(WorkflowResponse.error("Requirement is required"));
            }
            
            if (request.getTargetClass() == null || request.getTargetClass().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(WorkflowResponse.error("Target class is required"));
            }
            
            if (request.getRepoUrl() == null || request.getRepoUrl().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(WorkflowResponse.error("Repository URL is required"));
            }

            // Build initial state
            WorkflowState initialState = WorkflowState.builder()
                    .requirement(request.getRequirement())
                    .targetClass(request.getTargetClass())
                    .repoUrl(request.getRepoUrl())
                    .baseBranch(request.getBaseBranch() != null ? request.getBaseBranch() : "main")
                    .jiraUrl(request.getJiraUrl())
                    .logsPasted(request.getLogsPasted())
                    .userId(request.getUserId())
                    .build();

            // Start workflow (async execution)
            WorkflowState result = workflowService.startWorkflow(initialState);

            // Build response
            return ResponseEntity.ok(WorkflowResponse.fromState(result));

        } catch (Exception e) {
            log.error("Failed to start workflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(WorkflowResponse.error("Failed to start workflow: " + e.getMessage()));
        }
    }

    /**
     * Upload log files for analysis.
     * 
     * Used when user has log files to attach instead of pasting text.
     * 
     * POST /api/v1/workflows/upload-logs
     * Content-Type: multipart/form-data
     */
    @PostMapping("/upload-logs")
    public ResponseEntity<UploadResponse> uploadLogs(
            @RequestParam("files") List<MultipartFile> files) {
        
        log.info("📤 Uploading {} log files", files.size());

        try {
            List<FileUpload> uploads = new ArrayList<>();
            
            for (MultipartFile file : files) {
                FileUpload upload = FileUpload.builder()
                        .filename(file.getOriginalFilename())
                        .contentType(file.getContentType())
                        .size(file.getSize())
                        .content(file.getBytes())
                        .build();
                
                uploads.add(upload);
            }

            return ResponseEntity.ok(new UploadResponse(true, 
                    "Uploaded " + uploads.size() + " files", uploads));

        } catch (Exception e) {
            log.error("Failed to upload logs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new UploadResponse(false, "Upload failed: " + e.getMessage(), null));
        }
    }

    /**
     * Get workflow status.
     * 
     * GET /api/v1/workflows/{conversationId}/status
     * 
     * Response:
     * {
     *   "conversationId": "uuid",
     *   "status": "PAUSED",
     *   "currentAgent": "scope_discovery",
     *   "message": "Found 12 files. Approve scope?",
     *   "awaitingUserInput": true,
     *   "progress": 40
     * }
     */
    @GetMapping("/{conversationId}/status")
    public ResponseEntity<WorkflowResponse> getStatus(
            @PathVariable String conversationId) {
        
        log.info("📊 Getting status for conversation: {}", conversationId);

        try {
            WorkflowState state = workflowService.getWorkflowState(conversationId);
            
            if (state == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(WorkflowResponse.fromState(state));

        } catch (Exception e) {
            log.error("Failed to get status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(WorkflowResponse.error("Failed to get status: " + e.getMessage()));
        }
    }

    /**
     * Respond to ASK_DEV prompt.
     * 
     * Used when workflow is paused and needs user input.
     * 
     * POST /api/v1/workflows/{conversationId}/respond
     * 
     * Request body:
     * {
     *   "response": "Yes, proceed with those 12 files",
     *   "additionalContext": "Make sure to add unit tests"
     * }
     */
    @PostMapping("/{conversationId}/respond")
    public ResponseEntity<WorkflowResponse> respondToPrompt(
            @PathVariable String conversationId,
            @RequestBody UserResponse userResponse) {
        
        log.info("💬 User responding to workflow: {}", conversationId);

        try {
            // Get current state
            WorkflowState state = workflowService.getWorkflowState(conversationId);
            
            if (state == null) {
                return ResponseEntity.notFound().build();
            }

            if (!"PAUSED".equals(state.getWorkflowStatus())) {
                return ResponseEntity.badRequest()
                        .body(WorkflowResponse.error("Workflow is not paused"));
            }

            // Add user's response to conversation history
            state.addChatMessage("user", userResponse.getResponse());
            
            if (userResponse.getAdditionalContext() != null) {
                state.addChatMessage("user", userResponse.getAdditionalContext());
            }

            // Resume workflow
            WorkflowState result = workflowService.resumeWorkflow(state);

            return ResponseEntity.ok(WorkflowResponse.fromState(result));

        } catch (Exception e) {
            log.error("Failed to respond to workflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(WorkflowResponse.error("Failed to respond: " + e.getMessage()));
        }
    }

    /**
     * Get conversation history.
     * 
     * GET /api/v1/workflows/{conversationId}/history
     */
    @GetMapping("/{conversationId}/history")
    public ResponseEntity<ConversationHistory> getHistory(
            @PathVariable String conversationId) {
        
        log.info("📜 Getting history for conversation: {}", conversationId);

        try {
            WorkflowState state = workflowService.getWorkflowState(conversationId);
            
            if (state == null) {
                return ResponseEntity.notFound().build();
            }

            ConversationHistory history = new ConversationHistory();
            history.setConversationId(conversationId);
            history.setMessages(state.getConversationHistory());
            history.setStatus(state.getWorkflowStatus());

            return ResponseEntity.ok(history);

        } catch (Exception e) {
            log.error("Failed to get history", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Cancel a running workflow.
     * 
     * DELETE /api/v1/workflows/{conversationId}
     */
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> cancelWorkflow(
            @PathVariable String conversationId) {
        
        log.info("🛑 Cancelling workflow: {}", conversationId);

        try {
            workflowService.cancelWorkflow(conversationId);
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.error("Failed to cancel workflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ================================================================
    // REQUEST/RESPONSE DTOs
    // ================================================================

    @Data
    public static class StartWorkflowRequest {
        private String requirement;
        private String targetClass;
        private String repoUrl;
        private String baseBranch;
        private String jiraUrl;
        private String logsPasted;
        private String userId;
    }

    @Data
    public static class UserResponse {
        private String response;
        private String additionalContext;
    }

    @Data
    public static class WorkflowResponse {
        private boolean success;
        private String conversationId;
        private String status;
        private String currentAgent;
        private String message;
        private boolean awaitingUserInput;
        private int progress; // 0-100
        private String error;

        public static WorkflowResponse fromState(WorkflowState state) {
            WorkflowResponse response = new WorkflowResponse();
            response.setSuccess(true);
            response.setConversationId(state.getConversationId());
            response.setStatus(state.getWorkflowStatus());
            response.setCurrentAgent(state.getCurrentAgent());
            
            if (state.getLastAgentDecision() != null) {
                response.setMessage(state.getLastAgentDecision().getMessage());
                response.setAwaitingUserInput(
                    state.getLastAgentDecision().getNextStep() == 
                    com.purchasingpower.autoflow.workflow.state.AgentDecision.NextStep.ASK_DEV
                );
            }
            
            response.setProgress(calculateProgress(state));
            
            return response;
        }

        public static WorkflowResponse error(String errorMessage) {
            WorkflowResponse response = new WorkflowResponse();
            response.setSuccess(false);
            response.setError(errorMessage);
            return response;
        }

        private static int calculateProgress(WorkflowState state) {
            // Simple progress calculation based on current agent
            Map<String, Integer> agentProgress = Map.of(
                "requirement_analyzer", 5,
                "log_analyzer", 10,
                "code_indexer", 20,
                "scope_discovery", 30,
                "context_builder", 40,
                "code_generator", 50,
                "build_validator", 65,
                "test_runner", 75,
                "pr_reviewer", 85,
                "readme_generator", 90,
                "pr_creator", 95
            );
            
            return agentProgress.getOrDefault(state.getCurrentAgent(), 0);
        }
    }

    @Data
    public static class UploadResponse {
        private boolean success;
        private String message;
        private List<FileUpload> files;

        public UploadResponse(boolean success, String message, List<FileUpload> files) {
            this.success = success;
            this.message = message;
            this.files = files;
        }
    }

    @Data
    public static class ConversationHistory {
        private String conversationId;
        private List<com.purchasingpower.autoflow.workflow.state.ChatMessage> messages;
        private String status;
    }
}
