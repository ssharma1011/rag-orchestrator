package com.purchasingpower.autoflow.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.service.ConversationService;
import com.purchasingpower.autoflow.workflow.pipeline.PipelineEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/jira")
@RequiredArgsConstructor
public class JiraWebhookController {

    private final PipelineEngine pipelineEngine;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    /**
     * WEBHOOK 1: Issue status changed to "In Progress"
     * This is the PRIMARY webhook that starts the automation.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> handleStatusChange(@RequestBody String rawPayload) {
        log.info("Received JIRA status change webhook");

        try {
            JsonNode rootNode = objectMapper.readTree(rawPayload);

            // Validate payload
            if (!rootNode.has("issue") || !rootNode.path("issue").has("key")) {
                log.warn("Invalid webhook payload: Missing 'issue.key'");
                return ResponseEntity.badRequest().build();
            }

            String issueKey = rootNode.path("issue").path("key").asText();
            String status = rootNode.path("issue").path("fields").path("status").path("name").asText();

            log.info("Issue {} changed to status: {}", issueKey, status);

            // Only trigger on "In Progress" status
            if ("In Progress".equalsIgnoreCase(status)) {
                log.info("Triggering automation for: {}", issueKey);

                // ✅ NEW: Start conversation (not direct code generation)
                conversationService.startConversation(issueKey);

            } else {
                log.debug("Ignoring status change to: {}", status);
            }

            return ResponseEntity.accepted().build();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse JIRA webhook JSON", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * WEBHOOK 2: Comment added to issue
     * This handles developer replies to LLM questions.
     */
    @PostMapping(path = "/comment", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> handleComment(@RequestBody String rawPayload) {
        log.info("Received JIRA comment webhook");

        try {
            JsonNode rootNode = objectMapper.readTree(rawPayload);

            String issueKey = rootNode.path("issue").path("key").asText();
            String commentBody = rootNode.path("comment").path("body").asText();
            String commentAuthor = rootNode.path("comment").path("author").path("displayName").asText();
            String commentId = rootNode.path("comment").path("id").asText();

            log.info("Comment on {}: '{}' by {}", issueKey,
                    commentBody.substring(0, Math.min(50, commentBody.length())),
                    commentAuthor);

            // Skip if comment is from AutoFlow itself (avoid infinite loop)
            if (isFromAutoFlow(commentAuthor)) {
                log.debug("Skipping comment from AutoFlow bot");
                return ResponseEntity.ok().build();
            }

            // Handle developer reply
            conversationService.handleDeveloperReply(issueKey, commentBody, commentAuthor);

            return ResponseEntity.accepted().build();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse comment webhook JSON", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Check if comment is from AutoFlow bot (to avoid infinite loops)
     */
    private boolean isFromAutoFlow(String author) {
        // JIRA bot user is configured in application.yml
        return author != null && (
                author.equalsIgnoreCase("AutoFlow Bot") ||
                        author.equalsIgnoreCase("autoflow") ||
                        author.contains("[bot]")
        );
    }
}