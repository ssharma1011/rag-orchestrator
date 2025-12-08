package com.purchasingpower.autoflow.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> handleWebhook(@RequestBody String rawPayload) {
        log.info("Received Jira Webhook payload");

        try {
            // 1. Parse JSON just to extract the Key
            JsonNode rootNode = objectMapper.readTree(rawPayload);

            // 2. Validate essential fields
            if (!rootNode.has("issue") || !rootNode.path("issue").has("key")) {
                log.warn("Invalid webhook payload: Missing 'issue.key'");
                return ResponseEntity.badRequest().build();
            }

            String issueKey = rootNode.path("issue").path("key").asText();
            log.info("Triggering pipeline for Issue Key: {}", issueKey);

            // 3. Trigger Async Pipeline Engine
            // This returns a CompletableFuture, but we don't wait for it here
            pipelineEngine.run(issueKey);

            // 4. Return immediately to prevent Jira timeout
            return ResponseEntity.accepted().build();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse Jira webhook JSON", e);
            return ResponseEntity.badRequest().build();
        }
    }
}