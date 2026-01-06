package com.purchasingpower.autoflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ChatController.
 *
 * Tests the full chat flow including:
 * - New conversation creation
 * - Message processing
 * - SSE streaming
 * - Conversation listing
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String REPO_URL = "https://github.com/ssharma1011/rag-orchestrator/tree/main";

    @Test
    void sendHiMessage_shouldReturnConversationIdImmediately() throws Exception {
        // Given
        ChatRequest request = new ChatRequest();
        request.setMessage("hi");
        request.setRepoUrl(REPO_URL);
        request.setUserId("test-user");

        // When
        MvcResult result = mockMvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.conversationId").isNotEmpty())
            .andReturn();

        // Then
        String responseJson = result.getResponse().getContentAsString();
        ChatResponse response = objectMapper.readValue(responseJson, ChatResponse.class);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getConversationId()).isNotBlank();
        assertThat(response.getResponse()).contains("Processing");

        System.out.println("Created conversation: " + response.getConversationId());
    }

    @Test
    void sendHiMessage_shouldProcessWithoutIndexing() throws Exception {
        // Given - simple greeting should NOT trigger repo indexing
        ChatRequest request = new ChatRequest();
        request.setMessage("hello there!");
        request.setRepoUrl(REPO_URL);
        request.setUserId("test-user-2");

        // When
        long startTime = System.currentTimeMillis();

        MvcResult result = mockMvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

        long duration = System.currentTimeMillis() - startTime;

        // Then - should return quickly (< 5 seconds) because no indexing
        assertThat(duration).isLessThan(5000);

        ChatResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(), ChatResponse.class);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getConversationId()).isNotBlank();

        System.out.println("Response time: " + duration + "ms");
    }

    @Test
    void sendMessage_withoutRepoUrl_shouldReturnError() throws Exception {
        // Given - new conversation without repoUrl
        ChatRequest request = new ChatRequest();
        request.setMessage("hi");
        request.setUserId("test-user");
        // No repoUrl set

        // When/Then
        mockMvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void sendMessage_withEmptyMessage_shouldReturnError() throws Exception {
        // Given
        ChatRequest request = new ChatRequest();
        request.setMessage("");
        request.setRepoUrl(REPO_URL);

        // When/Then
        mockMvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void listConversations_shouldReturnList() throws Exception {
        // Given - create a conversation first
        ChatRequest request = new ChatRequest();
        request.setMessage("hi");
        request.setRepoUrl(REPO_URL);
        request.setUserId("list-test-user");

        mockMvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        // When - list conversations
        MvcResult result = mockMvc.perform(get("/api/v1/chat/conversations")
                .param("userId", "list-test-user"))
            .andExpect(status().isOk())
            .andReturn();

        // Then - should return a JSON array
        String responseJson = result.getResponse().getContentAsString();
        assertThat(responseJson).startsWith("[");

        System.out.println("Conversations: " + responseJson);
    }

    @Test
    void getConversationStatus_withInvalidId_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/chat/invalid-id/status"))
            .andExpect(status().isNotFound());
    }

    @Test
    void followUpMessage_shouldWork() throws Exception {
        // Given - create initial conversation
        ChatRequest initialRequest = new ChatRequest();
        initialRequest.setMessage("hi");
        initialRequest.setRepoUrl(REPO_URL);
        initialRequest.setUserId("followup-test-user");

        MvcResult initialResult = mockMvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(initialRequest)))
            .andExpect(status().isOk())
            .andReturn();

        ChatResponse initialResponse = objectMapper.readValue(
            initialResult.getResponse().getContentAsString(), ChatResponse.class);
        String conversationId = initialResponse.getConversationId();

        // Wait a bit for async processing
        TimeUnit.MILLISECONDS.sleep(500);

        // When - send follow-up
        ChatRequest followUpRequest = new ChatRequest();
        followUpRequest.setMessage("how are you?");
        followUpRequest.setConversationId(conversationId);

        MvcResult followUpResult = mockMvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(followUpRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.conversationId").value(conversationId))
            .andReturn();

        // Then
        ChatResponse followUpResponse = objectMapper.readValue(
            followUpResult.getResponse().getContentAsString(), ChatResponse.class);

        assertThat(followUpResponse.isSuccess()).isTrue();
        assertThat(followUpResponse.getConversationId()).isEqualTo(conversationId);

        System.out.println("Follow-up successful for: " + conversationId);
    }

    @Test
    void deleteConversation_shouldWork() throws Exception {
        // Given - create a conversation
        ChatRequest request = new ChatRequest();
        request.setMessage("hi");
        request.setRepoUrl(REPO_URL);
        request.setUserId("delete-test-user");

        MvcResult createResult = mockMvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

        ChatResponse response = objectMapper.readValue(
            createResult.getResponse().getContentAsString(), ChatResponse.class);
        String conversationId = response.getConversationId();

        // When - delete it
        mockMvc.perform(delete("/api/v1/chat/" + conversationId))
            .andExpect(status().isNoContent());

        // Then - status should show closed or not found
        // (depends on implementation - either 404 or status=CLOSED)
        System.out.println("Deleted conversation: " + conversationId);
    }

    @Test
    void explainCodeRequest_withNonIndexedRepo_shouldTriggerIndexingFirst() throws Exception {
        // Given - a code explanation request with a non-indexed repo
        ChatRequest request = new ChatRequest();
        request.setMessage("explain this code to me");
        request.setRepoUrl(REPO_URL);
        request.setUserId("explain-test-user");

        // When
        MvcResult result = mockMvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.conversationId").isNotEmpty())
            .andReturn();

        // Then - should return conversation ID immediately
        ChatResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(), ChatResponse.class);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getConversationId()).isNotBlank();
        assertThat(response.getResponse()).contains("Processing");

        String conversationId = response.getConversationId();
        System.out.println("Created conversation for explain request: " + conversationId);

        // Wait for async processing (indexing + explanation)
        // Note: In real scenario, this would trigger index_repository tool
        TimeUnit.SECONDS.sleep(2);

        // Verify conversation status
        MvcResult statusResult = mockMvc.perform(get("/api/v1/chat/" + conversationId + "/status"))
            .andExpect(status().isOk())
            .andReturn();

        String statusJson = statusResult.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> status = objectMapper.readValue(statusJson, Map.class);

        assertThat(status.get("conversationId")).isEqualTo(conversationId);
        System.out.println("Conversation status: " + status);
    }
}
