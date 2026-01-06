package com.purchasingpower.autoflow.api;

import com.purchasingpower.autoflow.agent.AutoFlowAgent;
import com.purchasingpower.autoflow.core.impl.RepositoryImpl;
import com.purchasingpower.autoflow.knowledge.GraphStore;
import com.purchasingpower.autoflow.knowledge.IndexingResult;
import com.purchasingpower.autoflow.knowledge.IndexingService;
import com.purchasingpower.autoflow.model.conversation.Conversation;
import com.purchasingpower.autoflow.model.dto.WorkflowResponse;
import com.purchasingpower.autoflow.service.ChatStreamService;
import com.purchasingpower.autoflow.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * REST controller for chat API.
 *
 * Simplified flow:
 * 1. User sends message with repoUrl
 * 2. System auto-indexes repo if needed
 * 3. AutoFlowAgent processes the message using tools
 * 4. Response returned with citations
 *
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final AutoFlowAgent agent;
    private final ConversationService conversationService;
    private final IndexingService indexingService;
    private final GraphStore graphStore;
    private final ChatStreamService chatStreamService;

    /**
     * Send a chat message.
     *
     * POST /api/v1/chat
     *
     * For new conversations, provide repoUrl.
     * For follow-up messages, provide conversationId.
     *
     * Returns conversationId immediately. Connect to SSE stream for updates.
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        try {
            // Validate message
            if (request.getMessage() == null || request.getMessage().isBlank()) {
                return ResponseEntity.badRequest()
                    .body(ChatResponse.error("Message is required"));
            }

            String conversationId = request.getConversationId();
            String repoUrl = request.getRepoUrl();
            String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
            String message = request.getMessage();
            String branch = request.getBranch();
            String mode = request.getMode();

            // New conversation - needs repoUrl
            if (conversationId == null || conversationId.isBlank()) {
                if (repoUrl == null || repoUrl.isBlank()) {
                    return ResponseEntity.badRequest()
                        .body(ChatResponse.error("repoUrl is required for new conversations. Example: {\"message\": \"explain this repo\", \"repoUrl\": \"https://github.com/user/repo\"}"));
                }

                conversationId = UUID.randomUUID().toString();
                log.info("New conversation: {} for repo: {}", conversationId, repoUrl);

                // Create conversation immediately (before indexing)
                Conversation conv = conversationService.createConversation(conversationId, userId, repoUrl);

                // Set conversation mode if provided
                if (mode != null && !mode.isBlank()) {
                    try {
                        conv.setMode(Conversation.ConversationMode.valueOf(mode.toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid mode '{}', using default EXPLORE", mode);
                        conv.setMode(Conversation.ConversationMode.EXPLORE);
                    }
                } else {
                    conv.setMode(Conversation.ConversationMode.EXPLORE);
                }

                conversationService.saveConversation(conv);

                // Return conversationId immediately - process async
                final String convId = conversationId;
                CompletableFuture.runAsync(() -> processMessageAsync(convId, repoUrl, branch, message, userId));

                return ResponseEntity.ok(ChatResponse.builder()
                    .success(true)
                    .conversationId(conversationId)
                    .response("Processing... Connect to SSE stream for updates.")
                    .build());

            } else {
                // Existing conversation - verify it exists
                Optional<Conversation> existing = conversationService.getConversation(conversationId);
                if (existing.isEmpty()) {
                    return ResponseEntity.badRequest()
                        .body(ChatResponse.error("Conversation not found: " + conversationId));
                }
                log.info("Continue conversation: {}", conversationId);

                // Process follow-up async too
                final String convId = conversationId;
                CompletableFuture.runAsync(() -> processFollowUpAsync(convId, message, userId));

                return ResponseEntity.ok(ChatResponse.builder()
                    .success(true)
                    .conversationId(conversationId)
                    .response("Processing... Connect to SSE stream for updates.")
                    .build());
            }

        } catch (Exception e) {
            log.error("Chat failed", e);
            return ResponseEntity.internalServerError()
                .body(ChatResponse.error("Internal error: " + e.getMessage()));
        }
    }

    /**
     * Process new conversation message asynchronously.
     *
     * Indexing is lazy - only happens when agent uses a code tool.
     * For greetings/conversation, no indexing needed.
     */
    private void processMessageAsync(String conversationId, String repoUrl, String branch, String message, String userId) {
        try {
            // RepoUrl already stored in conversation during creation
            // Branch info is embedded in URL (e.g., /tree/main)

            chatStreamService.sendThinking(conversationId, "Processing your message...");

            // Process with agent - it will decide if tools/indexing are needed
            WorkflowResponse result = agent.process(message, conversationId, userId);

            if (result.isSuccess()) {
                chatStreamService.sendComplete(conversationId, result.getMessage());
            } else {
                chatStreamService.sendError(conversationId, result.getError());
            }

        } catch (Exception e) {
            log.error("Async processing failed", e);
            chatStreamService.sendError(conversationId, "Processing failed: " + e.getMessage());
        }
    }

    /**
     * Ensures repo is indexed with progress updates via SSE.
     */
    private String ensureRepoIndexedWithProgress(String conversationId, String repoUrl, String branch) {
        try {
            // Normalize URL - remove /tree/branch, /blob/branch, etc.
            String normalizedUrl = normalizeRepoUrl(repoUrl);
            log.debug("Checking if repo already indexed. Raw: {}, Normalized: {}", repoUrl, normalizedUrl);

            // Check if already indexed using normalized URL
            String checkCypher = "MATCH (r:Repository) WHERE r.url CONTAINS $urlBase RETURN r.id as id, r.url as url LIMIT 1";
            String urlBase = extractRepoBase(normalizedUrl);
            List<Map<String, Object>> existing = graphStore.executeCypherQueryRaw(
                checkCypher, Map.of("urlBase", urlBase)
            );

            if (!existing.isEmpty() && existing.get(0).get("id") != null) {
                String repoId = existing.get(0).get("id").toString();
                log.info("Repo already indexed: {} (matched: {})", repoId, existing.get(0).get("url"));
                chatStreamService.sendThinking(conversationId, "Repository already indexed ✓");
                return repoId;
            }

            // Index the repo with progress updates
            chatStreamService.sendThinking(conversationId, "Indexing repository (this may take a minute)...");

            log.info("Indexing repo: {}", normalizedUrl);
            RepositoryImpl repo = RepositoryImpl.builder()
                .url(normalizedUrl)
                .branch(branch != null ? branch : "main")
                .language("Java")
                .build();

            IndexingResult result = indexingService.indexRepository(repo);

            if (result.isSuccess()) {
                log.info("Indexed {} entities", result.getEntitiesCreated());
                chatStreamService.sendThinking(conversationId,
                    String.format("Indexed %d code entities ✓", result.getEntitiesCreated()));
                return repo.getId();
            } else {
                log.error("Indexing failed: {}", result.getErrors());
                return null;
            }

        } catch (Exception e) {
            log.error("Failed to ensure repo indexed", e);
            return null;
        }
    }

    /**
     * Process follow-up message asynchronously.
     */
    private void processFollowUpAsync(String conversationId, String message, String userId) {
        try {
            chatStreamService.sendThinking(conversationId, "Processing your question...");

            WorkflowResponse result = agent.process(message, conversationId, userId);

            if (result.isSuccess()) {
                chatStreamService.sendComplete(conversationId, result.getMessage());
            } else {
                chatStreamService.sendError(conversationId, result.getError());
            }

        } catch (Exception e) {
            log.error("Async follow-up processing failed", e);
            chatStreamService.sendError(conversationId, "Processing failed: " + e.getMessage());
        }
    }

    /**
     * Normalize repository URL by removing branch paths.
     */
    private String normalizeRepoUrl(String url) {
        if (url == null) return null;
        // Remove /tree/branch, /blob/branch patterns
        return url.replaceAll("/tree/[^/]+$", "")
                  .replaceAll("/blob/[^/]+$", "")
                  .replaceAll("/-/tree/[^/]+$", "")
                  .replaceAll("\\?.*$", ""); // Remove query params
    }

    /**
     * Extract the base repo identifier (owner/repo) for fuzzy matching.
     */
    private String extractRepoBase(String url) {
        if (url == null) return "";
        // Extract "owner/repo" from github.com/owner/repo
        String path = url.replaceAll("https?://[^/]+/", "");
        String[] parts = path.split("/");
        if (parts.length >= 2) {
            return parts[0] + "/" + parts[1];
        }
        return path;
    }

    /**
     * Get conversation history.
     *
     * GET /api/v1/chat/{id}/history
     */
    @GetMapping("/{conversationId}/history")
    public ResponseEntity<ConversationHistory> getHistory(@PathVariable String conversationId) {
        try {
            Optional<Conversation> conversation = conversationService.getConversationWithMessages(conversationId);

            if (conversation.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Conversation conv = conversation.get();
            List<ConversationHistory.Message> messages = conv.getMessages().stream()
                .map(m -> ConversationHistory.Message.builder()
                    .role(m.getRole())
                    .content(m.getContent())
                    .timestamp(m.getTimestamp().toString())
                    .build())
                .collect(Collectors.toList());

            return ResponseEntity.ok(ConversationHistory.builder()
                .conversationId(conversationId)
                .messages(messages)
                .build());

        } catch (Exception e) {
            log.error("Failed to get history", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * End/delete a conversation.
     *
     * DELETE /api/v1/chat/{id}
     */
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String conversationId) {
        try {
            conversationService.closeConversation(conversationId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to delete conversation", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * List all conversations for a user.
     *
     * GET /api/v1/chat/conversations?userId=xxx
     */
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationSummary>> listConversations(
            @RequestParam(defaultValue = "anonymous") String userId) {
        try {
            List<Conversation> conversations = conversationService.getActiveConversations(userId);

            List<ConversationSummary> summaries = conversations.stream()
                .map(c -> ConversationSummary.builder()
                    .conversationId(c.getConversationId())
                    .repoUrl(c.getRepoUrl())
                    .repoName(c.getRepoName())
                    .status(c.isActive() ? "ACTIVE" : "CLOSED")
                    .createdAt(c.getCreatedAt() != null ? c.getCreatedAt().toString() : null)
                    .lastActivity(c.getLastActivity() != null ? c.getLastActivity().toString() : null)
                    .messageCount(0) // Don't access lazy collection - would need separate query
                    .build())
                .collect(Collectors.toList());

            return ResponseEntity.ok(summaries);

        } catch (Exception e) {
            log.error("Failed to list conversations", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get conversation status.
     *
     * GET /api/v1/chat/{id}/status
     */
    @GetMapping("/{conversationId}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String conversationId) {
        try {
            Optional<Conversation> conversation = conversationService.getConversation(conversationId);

            if (conversation.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Conversation conv = conversation.get();
            Map<String, Object> status = Map.of(
                "conversationId", conversationId,
                "status", conv.isActive() ? "ACTIVE" : "CLOSED",
                "mode", conv.getMode() != null ? conv.getMode().name() : "EXPLORE",
                "repoUrl", conv.getRepoUrl() != null ? conv.getRepoUrl() : "",
                "repoName", conv.getRepoName() != null ? conv.getRepoName() : "",
                "hasActiveStream", chatStreamService.hasActiveStream(conversationId)
            );

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Failed to get conversation status", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * SSE stream for real-time updates.
     *
     * GET /api/v1/chat/{id}/stream
     *
     * Returns Server-Sent Events with chat updates:
     * - THINKING: Agent is processing
     * - TOOL: Agent is executing a tool
     * - PARTIAL: Streaming response tokens
     * - COMPLETE: Final response
     * - ERROR: Error occurred
     */
    @GetMapping(value = "/{conversationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String conversationId) {
        log.info("Client connected to chat stream: {}", conversationId);
        return chatStreamService.createStream(conversationId);
    }

    /**
     * DTO for conversation list.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ConversationSummary {
        private String conversationId;
        private String repoUrl;
        private String repoName;
        private String status;
        private String createdAt;
        private String lastActivity;
        private int messageCount;
    }
}
