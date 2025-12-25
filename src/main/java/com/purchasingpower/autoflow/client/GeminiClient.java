package com.purchasingpower.autoflow.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.configuration.AppProperties;
import com.purchasingpower.autoflow.model.llm.CodeGenerationResponse;
import com.purchasingpower.autoflow.model.metrics.LLMCallMetrics;
import com.purchasingpower.autoflow.service.LLMMetricsService;
import com.purchasingpower.autoflow.service.PromptLibraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final PromptLibraryService promptLibrary;
    private final LLMMetricsService llmMetricsService;

    private WebClient geminiWebClient;

    @PostConstruct
    public void init() {
        this.geminiWebClient = WebClient.builder()
                .baseUrl(props.getGemini().getBaseUrl())
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                        .build())
                .build();
    }

    private String getApiUrl(String model, String action) {
        return String.format("/%s/models/%s:%s?key=%s",
                props.getGemini().getApiVersion(), model, action, props.getGemini().getApiKey());
    }

    public List<List<Double>> batchCreateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) return new ArrayList<>();
        List<List<Double>> allEmbeddings = new ArrayList<>();
        int batchSize = 100;
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            allEmbeddings.addAll(callBatchEmbeddingApi(texts.subList(i, end)));
        }
        return allEmbeddings;
    }

    private List<List<Double>> callBatchEmbeddingApi(List<String> batch) {
        String model = props.getGemini().getEmbeddingModel();
        String url = getApiUrl(model, "batchEmbedContents");

        List<Map<String, Object>> requests = batch.stream()
                .map(text -> Map.of("model", "models/" + model, "content", Map.of("parts", List.of(Map.of("text", text)))))
                .toList();

        try {
            JsonNode response = geminiWebClient.post().uri(url).bodyValue(Map.of("requests", requests))
                    .retrieve().bodyToMono(JsonNode.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)).filter(this::isRetryable)).block();

            List<List<Double>> embeddings = new ArrayList<>();
            if (response != null && response.path("embeddings").isArray()) {
                for (JsonNode node : response.path("embeddings")) {
                    embeddings.add(objectMapper.convertValue(node.path("values"), List.class));
                }
            }
            return embeddings;
        } catch (Exception e) {
            log.error("Batch embedding failed", e);
            throw new RuntimeException("Failed to embed", e);
        }
    }

    public List<Double> createEmbedding(String text) {
        List<List<Double>> result = batchCreateEmbeddings(List.of(text));
        return result.isEmpty() ? new ArrayList<>() : result.get(0);
    }

    public CodeGenerationResponse generateScaffold(String requirements, String repoName) {
        String prompt = promptLibrary.render("architect", Map.of("requirements", requirements, "repoName", repoName));
        return callChatApi(prompt);
    }

    public CodeGenerationResponse generateCodePlan(String requirements, String context) {
        String prompt = promptLibrary.render("maintainer", Map.of("requirements", requirements, "context", context));
        return callChatApi(prompt);
    }

    public CodeGenerationResponse generateFix(String buildLogs, String originalRequirements) {
        String prompt = promptLibrary.render("fix-compiler-errors", Map.of("requirements", originalRequirements, "errors", buildLogs));
        return callChatApi(prompt);
    }

    private CodeGenerationResponse callChatApi(String prompt) {
        String model = props.getGemini().getChatModel();
        String url = getApiUrl(model, "generateContent");
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json", "temperature", 0.2));

        try {
            String json = geminiWebClient.post().uri(url).bodyValue(body)
                    .retrieve().bodyToMono(String.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(10)).filter(this::isRetryable)).block();
            return parseResponse(json);
        } catch (Exception e) {
            log.error("Gemini Chat failed", e);
            throw new RuntimeException("Failed to generate code plan");
        }
    }

    /**
     * Generate text with full logging and metrics.
     * Delegates to callChatApi for consistent instrumentation.
     */
    public String generateText(String prompt) {
        return callChatApi(prompt, "generateText", null);
    }

    private boolean isRetryable(Throwable ex) {
        return ex instanceof WebClientResponseException.TooManyRequests || ex instanceof WebClientResponseException.InternalServerError;
    }

    private CodeGenerationResponse parseResponse(String rawJson) throws Exception {
        JsonNode root = objectMapper.readTree(rawJson);
        String content = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
        return objectMapper.readValue(content, CodeGenerationResponse.class);
    }

    /**
     * Generic LLM chat call for agent prompts with full metrics tracking.
     * Returns raw text response (not structured JSON).
     *
     * This method:
     * 1. Calls Gemini API
     * 2. Tracks metrics (tokens, cost, latency)
     * 3. Logs request/response
     * 4. Handles retries
     *
     * @param prompt The full prompt to send
     * @param agentName Name of calling agent (for logging/metrics)
     * @return Raw text response from LLM
     */
    public String callChatApi(String prompt, String agentName) {
        return callChatApi(prompt, agentName, null);
    }

    /**
     * Overload with conversation ID for better metrics tracking
     */
    public String callChatApi(String prompt, String agentName, String conversationId) {
        log.info("═══════════════════════════════════════════════════════════");
        log.info("📤 GEMINI API CALL [{}]", agentName);
        log.info("═══════════════════════════════════════════════════════════");
        log.info("Call ID: {}", UUID.randomUUID().toString().substring(0, 8));
        log.info("Conversation ID: {}", conversationId != null ? conversationId : "N/A");
        log.info("Prompt length: {} chars", prompt.length());
        log.info("\n📝 PROMPT:\n{}\n", truncateForLog(prompt, 2000));

        String callId = UUID.randomUUID().toString();
        String model = props.getGemini().getChatModel();
        String url = getApiUrl(model, "generateContent");

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", 0.7)
        );

        long startTime = System.currentTimeMillis();
        LLMCallMetrics metrics = LLMCallMetrics.builder()
                .callId(callId)
                .agentName(agentName)
                .conversationId(conversationId)
                .timestamp(LocalDateTime.now())
                .model(model)
                .prompt(prompt)
                .promptLength(prompt.length())
                .temperature(0.7)
                .build();

        try {
            String json = geminiWebClient.post().uri(url).bodyValue(body)
                    .retrieve().bodyToMono(String.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(10))
                            .filter(this::isRetryable)
                            .doBeforeRetry(signal -> {
                                log.warn("Retrying LLM call [{}] - Attempt {}", agentName, signal.totalRetries() + 1);
                                metrics.setRetryCount((int) signal.totalRetries() + 1);
                            }))
                    .block();

            long latency = System.currentTimeMillis() - startTime;

            JsonNode root = objectMapper.readTree(json);
            String response = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            // Extract token usage
            JsonNode usageMetadata = root.path("usageMetadata");
            int inputTokens = usageMetadata.path("promptTokenCount").asInt(0);
            int outputTokens = usageMetadata.path("candidatesTokenCount").asInt(0);

            // Complete metrics
            metrics.setLatencyMs(latency);
            metrics.setResponse(response);
            metrics.setResponseLength(response.length());
            metrics.setSuccess(true);
            metrics.setInputTokens(inputTokens);
            metrics.setOutputTokens(outputTokens);
            metrics.setTotalTokens(inputTokens + outputTokens);
            metrics.setHttpStatusCode(200);

            double cost = metrics.calculateCost();
            double tokensPerSec = metrics.calculateTokensPerSecond();

            log.info("═══════════════════════════════════════════════════════════");
            log.info("📥 GEMINI API RESPONSE [{}]", agentName);
            log.info("═══════════════════════════════════════════════════════════");
            log.info("✅ Status: SUCCESS");
            log.info("⏱️  Latency: {}ms", latency);
            log.info("🎯 Tokens: {} input + {} output = {} total", inputTokens, outputTokens, inputTokens + outputTokens);
            log.info("💰 Cost: ${}", String.format("%.4f", cost));
            log.info("⚡ Throughput: {} tokens/sec", String.format("%.1f", tokensPerSec));
            log.info("\n📝 RESPONSE:\n{}\n", truncateForLog(response, 2000));
            log.info("═══════════════════════════════════════════════════════════\n");

            // Record metrics (async, non-blocking)
            if (llmMetricsService != null) {
                llmMetricsService.recordCall(metrics);
            }

            return response;

        } catch (WebClientResponseException e) {
            long latency = System.currentTimeMillis() - startTime;

            metrics.setLatencyMs(latency);
            metrics.setSuccess(false);
            metrics.setErrorMessage(e.getMessage());
            metrics.setHttpStatusCode(e.getStatusCode().value());

            log.error("❌ LLM Call Failed [{}]", agentName);
            log.error("   Status: {}", e.getStatusCode());
            log.error("   Message: {}", e.getMessage());

            // Record failure metrics
            if (llmMetricsService != null) {
                llmMetricsService.recordCall(metrics);
            }

            throw new RuntimeException("Gemini API call failed for agent: " + agentName, e);

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;

            metrics.setLatencyMs(latency);
            metrics.setSuccess(false);
            metrics.setErrorMessage(e.getMessage());

            log.error("❌ LLM Call Failed [{}]", agentName, e);

            // Record failure metrics
            if (llmMetricsService != null) {
                llmMetricsService.recordCall(metrics);
            }

            throw new RuntimeException("Gemini API call failed for agent: " + agentName, e);
        }
    }

    /**
     * Truncate long strings for logging (to avoid massive log files).
     * Shows first N characters + ellipsis if truncated.
     */
    private String truncateForLog(String text, int maxLength) {
        if (text == null) {
            return "(null)";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "\n... [TRUNCATED - " + (text.length() - maxLength) + " more chars]";
    }

}