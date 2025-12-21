package com.purchasingpower.autoflow.controller;

import com.purchasingpower.autoflow.service.WorkflowExecutionService;
import com.purchasingpower.autoflow.workflow.state.WorkflowState;
import com.purchasingpower.autoflow.workflow.state.FileUpload;
import com.purchasingpower.autoflow.workflow.state.ChatMessage;
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
import static java.util.Map.entry;

@Slf4j
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WorkflowController {

    private final WorkflowExecutionService workflowService;

    @PostMapping("/start")
    public ResponseEntity<WorkflowResponse> startWorkflow(@RequestBody StartWorkflowRequest request) {
        log.info("Starting workflow for: {}", request.getRequirement());

        try {
            WorkflowState initialState = WorkflowState.builder()
                    .requirement(request.getRequirement())
                    .targetClass(request.getTargetClass())
                    .repoUrl(request.getRepoUrl())
                    .baseBranch(request.getBaseBranch() != null ? request.getBaseBranch() : "main")
                    .jiraUrl(request.getJiraUrl())
                    .logsPasted(request.getLogsPasted())
                    .userId(request.getUserId())
                    .build();

            // Pass WorkflowState directly. Service must accept WorkflowState.
            WorkflowState result = workflowService.startWorkflow(initialState);
            return ResponseEntity.ok(WorkflowResponse.fromState(result));

        } catch (Exception e) {
            log.error("Failed to start workflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(WorkflowResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/upload-logs")
    public ResponseEntity<UploadResponse> uploadLogs(@RequestParam("files") List<MultipartFile> files) {
        try {
            List<FileUpload> uploads = new ArrayList<>();
            for (MultipartFile file : files) {
                FileUpload upload = FileUpload.builder()
                        .fileName(file.getOriginalFilename())
                        .contentType(file.getContentType())
                        .sizeBytes(file.getSize()) // Fixed: Matches field name in FileUpload
                        .uploadedAt(System.currentTimeMillis())
                        .fileType(FileUpload.detectType(file.getOriginalFilename()))
                        .build();
                uploads.add(upload);
            }
            return ResponseEntity.ok(new UploadResponse(true, "Uploaded " + uploads.size() + " files", uploads));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new UploadResponse(false, e.getMessage(), null));
        }
    }

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

    @PostMapping("/{conversationId}/respond")
    public ResponseEntity<WorkflowResponse> respondToPrompt(
            @PathVariable String conversationId,
            @RequestBody UserResponse userResponse) {
        try {
            WorkflowState state = workflowService.getWorkflowState(conversationId);
            if (state == null) return ResponseEntity.notFound().build();

            state.addChatMessage("user", userResponse.getResponse());
            if (userResponse.getAdditionalContext() != null) {
                state.addChatMessage("user", "Context: " + userResponse.getAdditionalContext());
            }

            WorkflowState result = workflowService.resumeWorkflow(state);
            return ResponseEntity.ok(WorkflowResponse.fromState(result));

        } catch (Exception e) {
            log.error("Failed to resume workflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(WorkflowResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{conversationId}/history")
    public ResponseEntity<ConversationHistory> getHistory(@PathVariable String conversationId) {
        try {
            WorkflowState state = workflowService.getWorkflowState(conversationId);
            if (state == null) return ResponseEntity.notFound().build();

            ConversationHistory history = new ConversationHistory();
            history.setConversationId(conversationId);
            history.setMessages(state.getConversationHistory());
            history.setStatus(state.getWorkflowStatus());

            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> cancelWorkflow(@PathVariable String conversationId) {
        try {
            workflowService.cancelWorkflow(conversationId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

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
    public static class UploadResponse {
        private boolean success;
        private String message;
        private List<FileUpload> files;
        public UploadResponse(boolean s, String m, List<FileUpload> f) { success=s; message=m; files=f; }
    }

    @Data
    public static class ConversationHistory {
        private String conversationId;
        private List<ChatMessage> messages;
        private String status;
    }

    @Data
    public static class WorkflowResponse {
        private boolean success;
        private String conversationId;
        private String status;
        private String currentAgent;
        private String message;
        private boolean awaitingUserInput;
        private int progress;
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

        public static WorkflowResponse error(String msg) {
            WorkflowResponse r = new WorkflowResponse();
            r.setSuccess(false);
            r.setError(msg);
            return r;
        }

        private static int calculateProgress(WorkflowState state) {
            Map<String, Integer> agentProgress = Map.ofEntries(
                    entry("requirement_analyzer", 5),
                    entry("log_analyzer", 10),
                    entry("code_indexer", 20),
                    entry("scope_discovery", 30),
                    entry("context_builder", 40),
                    entry("code_generator", 50),
                    entry("build_validator", 65),
                    entry("test_runner", 75),
                    entry("pr_reviewer", 85),
                    entry("readme_generator", 90),
                    entry("pr_creator", 95)
            );
            return agentProgress.getOrDefault(state.getCurrentAgent(), 0);
        }
    }
}