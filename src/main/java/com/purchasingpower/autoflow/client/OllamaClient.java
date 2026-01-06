package com.purchasingpower.autoflow.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.model.metrics.LLMCallMetrics;
import com.purchasingpower.autoflow.service.LLMMetricsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ollama LLM provider implementation.
 * Supports local models (Qwen 2.5 Coder 32B) for chat and embeddings.
 * Bypasses cloud API rate limits and costs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaClient implements LLMProvider {

    private final ObjectMapper objectMapper;
    private final LLMMetricsService llmMetricsService;
    private WebClient ollamaWebClient;

    @Value("${app.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${app.ollama.chat-model:qwen2.5-coder:32b}")
    private String chatModel;

    @Value("${app.ollama.embedding-model:mxbai-embed-large}")
    private String embeddingModel;

    @Value("${app.ollama.num-ctx:32768}")
    private int numCtx;

/*
    @PostConstruct
    public void init() {
        this.ollamaWebClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
*/

    @Override
    public String getProviderName() {
        return "Ollama (" + chatModel + ")";
    }

    @Override
    public String chat(String prompt, String agentName, String conversationId) {
        return callChatApi(prompt, agentName, conversationId);
    }


    @PostConstruct
    public void init() {
        // 1. Create a custom HttpClient with high timeouts
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000) // 10s to connect
                .responseTimeout(Duration.ofMinutes(60))           // 60m to wait for model response
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.MINUTES))
                        .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.MINUTES)));

        // 2. Build the WebClient using this connector
        this.ollamaWebClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * Executes a chat call to Ollama using the /api/chat endpoint.
     * Structured for high-precision tool orchestration with large-scale models.
     */
    public String callChatApi(String prompt, String agentName, String conversationId) {
        log.info("🔵 [LLM REQUEST] Provider=Ollama, Agent={}, Model={}", agentName, chatModel);
        log.debug("🔵 [LLM REQUEST] Prompt length={}, First 200 chars: {}",
            prompt.length(),
            prompt.substring(0, Math.min(200, prompt.length())));

        long startTime = System.currentTimeMillis();

        // Using the Chat API structure to separate System instructions from User data.
        // Qwen 3 30B utilizes this separation to strictly follow the Orchestrator persona.
        Map<String, Object> body = Map.of(
                "model", chatModel,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are the AutoFlow Orchestrator. Output only valid JSON. Do not include conversational filler."),
                        Map.of("role", "user", "content", prompt)
                ),
                "stream", false,
                "format", "json", // Native JSON mode for deterministic tool calling
                "options", Map.of(
                        "num_ctx", numCtx,
                        "temperature", 0.0,
                        "num_predict", 4096, // Headroom for generating complex Java code
                        "repeat_penalty", 1.1
                )
        );

        try {
            JsonNode response = ollamaWebClient.post()
                    .uri("/api/chat")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            // Extract content from message role (Ollama Chat API format)
            String content = response.path("message").path("content").asText();
            long latency = System.currentTimeMillis() - startTime;

            log.info("🟢 [LLM RESPONSE] Provider=Ollama, Latency={}ms, ResponseLength={}", latency, content.length());
            log.debug("🟢 [LLM RESPONSE] Content: {}", content.substring(0, Math.min(500, content.length())));

            // Track metrics for performance auditing and comparison against Cloud models
            LLMCallMetrics metrics = LLMCallMetrics.builder()
                    .callId(UUID.randomUUID().toString())
                    .agentName(agentName)
                    .conversationId(conversationId)
                    .timestamp(LocalDateTime.now())
                    .model(chatModel)
                    .latencyMs(latency)
                    .response(content)
                    .success(true)
                    .build();

            if (llmMetricsService != null) {
                llmMetricsService.recordCall(metrics);
            }

            return content;

        } catch (Exception e) {
            log.error("🔴 Ollama call failed for model {}: {}", chatModel, e.getMessage());
            throw new RuntimeException("Local AI Engine (Ollama) failed. Ensure the " + chatModel + " model is fully downloaded and Ollama is running.", e);
        }
    }

    @Override
    public List<Double> embed(String text) {
        log.info("🔵 [EMBEDDING REQUEST] Provider=Ollama, Model={}, TextLength={}", embeddingModel, text.length());
        log.debug("🔵 [EMBEDDING REQUEST] Text preview: {}", text.substring(0, Math.min(100, text.length())));

        Map<String, Object> body = Map.of(
                "model", embeddingModel,
                "prompt", text
        );

        try {
            JsonNode response = ollamaWebClient.post()
                    .uri("/api/embeddings")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            // Extract embedding array from response
            List<Double> embedding = objectMapper.convertValue(
                    response.path("embedding"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class)
            );

            log.info("🟢 [EMBEDDING RESPONSE] Provider=Ollama, Dimensions={}", embedding.size());
            log.debug("🟢 [EMBEDDING RESPONSE] First 5 values: {}", embedding.subList(0, Math.min(5, embedding.size())));
            return embedding;

        } catch (Exception e) {
            log.error("🔴 Ollama embedding failed for model {}: {}", embeddingModel, e.getMessage());
            throw new RuntimeException("Embedding generation failed. Ensure " + embeddingModel + " is downloaded.", e);
        }
    }

    @Override
    public List<List<Double>> embedBatch(List<String> texts) {
        // Ollama doesn't have batch API, call sequentially
        return texts.stream()
                .map(this::embed)
                .toList();
    }
}