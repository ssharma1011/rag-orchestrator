package com.purchasingpower.autoflow.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.config.GeminiConfig;
import com.purchasingpower.autoflow.configuration.AppProperties;
import com.purchasingpower.autoflow.model.ServiceType;
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
import reactor.util.retry.RetryBackoffSpec;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enhanced Client for interacting with Gemini API.
 * FIXED: RPM Throttling for embeddings (4s delay) to preserve Chat quota.
 * FIXED: Aggressive Quota-Aware Backoff (up to 12 retries) to clear the 1-min window.
 * FIXED: Restored generateText method and corrected RetrySignal compilation issues.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final AppProperties props;
    private final GeminiConfig geminiConfig;
    private final ObjectMapper objectMapper;
    private final PromptLibraryService promptLibrary;
    private final LLMMetricsService llmMetricsService;

    private WebClient geminiWebClient;

    @PostConstruct
    public void init() {
        this.geminiWebClient = WebClient.builder()
                .baseUrl(props.getGemini().getBaseUrl())
                .defaultHeader("x-goog-api-key", props.getGemini().getApiKey())
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                        .build())
                .build();
    }

    private String getApiUrl(String model, String action) {
        return String.format("/%s/models/%s:%s", props.getGemini().getApiVersion(), model, action);
    }

    /**
     * Extreme Retry Spec.
     * Allows 12 retries spanning ~180 seconds. This is guaranteed to outlast
     * even the harshest 1-minute quota locks.
     */
    private RetryBackoffSpec buildRetrySpec(String label) {
        return Retry.backoff(12, Duration.ofSeconds(4))
                .maxBackoff(Duration.ofSeconds(30))
                .jitter(0.9)
                .filter(this::isRetryable)
                .doBeforeRetry(signal -> log.warn("[{}] Gemini Quota/Limit hit. Retrying (Attempt {}/12). Last error: {}",
                        label, signal.totalRetries() + 1, signal.failure().getMessage()))
                .onRetryExhaustedThrow((spec, signal) -> {
                    log.error("[{}] Gemini API permanently failed after 12 retries.", label);
                    return new RuntimeException("Gemini API is unavailable due to heavy load. Please wait 1 minute.", signal.failure());
                });
    }

    public List<List<Double>> batchCreateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) return new ArrayList<>();
        List<List<Double>> allEmbeddings = new ArrayList<>();
        int batchSize = 50;

        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());

            // AGGRESSIVE RPM THROTTLING: 4 seconds between batches.
            // This ensures indexing doesn't steal 100% of the 15 RPM quota.
            if (i > 0) {
                try { Thread.sleep(4000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }

            allEmbeddings.addAll(callBatchEmbeddingApi(texts.subList(i, end)));
        }
        return allEmbeddings;
    }

    private List<List<Double>> callBatchEmbeddingApi(List<String> batch) {
        String model = props.getGemini().getEmbeddingModel();
        String url = getApiUrl(model, "batchEmbedContents");
        long startTime = System.currentTimeMillis();

        List<Map<String, Object>> requests = batch.stream()
                .map(text -> Map.of("model", "models/" + model, "content", Map.of("parts", List.of(Map.of("text", text)))))
                .toList();

        try {
            JsonNode response = geminiWebClient.post().uri(url).bodyValue(Map.of("requests", requests))
                    .retrieve().bodyToMono(JsonNode.class)
                    .retryWhen(buildRetrySpec("Embedding")).block();

            List<List<Double>> embeddings = new ArrayList<>();
            if (response != null && response.path("embeddings").isArray()) {
                for (JsonNode node : response.path("embeddings")) {
                    embeddings.add(objectMapper.convertValue(node.path("values"), List.class));
                }
            }
            return embeddings;
        } catch (Exception e) {
            log.error("Embedding failed: {}", e.getMessage());
            throw new RuntimeException("Embedding quota failure", e);
        }
    }

    public List<Double> createEmbedding(String text) {
        List<List<Double>> result = batchCreateEmbeddings(List.of(text));
        return result.isEmpty() ? new ArrayList<>() : result.get(0);
    }

    public CodeGenerationResponse generateScaffold(String req, String repo) {
        return callChatApiStructured(promptLibrary.render("architect", Map.of("requirements", req, "repoName", repo)), "architect");
    }

    public CodeGenerationResponse generateCodePlan(String req, String ctx) {
        return callChatApiStructured(promptLibrary.render("maintainer", Map.of("requirements", req, "context", ctx)), "maintainer");
    }

    public CodeGenerationResponse generateFix(String logs, String req) {
        return callChatApiStructured(promptLibrary.render("fix-compiler-errors", Map.of("requirements", req, "errors", logs)), "fix-compiler-errors");
    }

    /**
     * RESTORED: Generate raw text with full logging and metrics.
     * Delegates to callChatApi for consistent instrumentation.
     */
    public String generateText(String prompt) {
        return callChatApi(prompt, "generateText");
    }

    private CodeGenerationResponse callChatApiStructured(String prompt, String agent) {
        try {
            return parseResponse(callChatApi(prompt, agent, null, true));
        } catch (Exception e) {
            throw new RuntimeException("AI structured response failed", e);
        }
    }

    private boolean isRetryable(Throwable ex) {
        if (ex instanceof WebClientResponseException webEx) {
            int status = webEx.getStatusCode().value();
            return status == 429 || webEx.getStatusCode().is5xxServerError();
        }
        return ex instanceof java.io.IOException;
    }

    private CodeGenerationResponse parseResponse(String rawJson) throws Exception {
        JsonNode root = objectMapper.readTree(rawJson);
        String content = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
        return objectMapper.readValue(content, CodeGenerationResponse.class);
    }

    public String callChatApi(String prompt, String agentName) {
        return callChatApi(prompt, agentName, null, false);
    }

    public String callChatApi(String prompt, String agentName, String conversationId) {
        return callChatApi(prompt, agentName, conversationId, false);
    }

    private String callChatApi(String prompt, String agentName, String conversationId, boolean isJsonMode) {
        log.info("🔴 Gemini Request: Agent={}, Length={}", agentName, prompt.length());

        var callCtx = com.purchasingpower.autoflow.util.ExternalCallLogger.startCall(ServiceType.GEMINI, "generateContent", log);
        String model = props.getGemini().getChatModel();
        String url = getApiUrl(model, "generateContent");
        double temp = isJsonMode ? geminiConfig.getJsonTemperature() : geminiConfig.getTemperatureForAgent(agentName);

        Map<String, Object> genConfig = new HashMap<>();
        genConfig.put("temperature", temp);
        if (isJsonMode) genConfig.put("responseMimeType", "application/json");

        Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))), "generationConfig", genConfig);
        long startTime = System.currentTimeMillis();

        LLMCallMetrics metrics = LLMCallMetrics.builder().callId(UUID.randomUUID().toString()).agentName(agentName).conversationId(conversationId).timestamp(LocalDateTime.now()).model(model).prompt(prompt).promptLength(prompt.length()).temperature(temp).build();

        try {
            String json = geminiWebClient.post().uri(url).bodyValue(body)
                    .retrieve().bodyToMono(String.class)
                    .retryWhen(buildRetrySpec("Chat-" + agentName)).block();

            JsonNode root = objectMapper.readTree(json);
            String response = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            JsonNode usage = root.path("usageMetadata");

            metrics.setLatencyMs(System.currentTimeMillis() - startTime);
            metrics.setResponse(response);
            metrics.setSuccess(true);
            metrics.setHttpStatusCode(200);

            if (llmMetricsService != null) llmMetricsService.recordCall(metrics);
            callCtx.logResponse("Success", "Tokens", usage.path("totalTokenCount").asInt(0));

            return response;
        } catch (Exception e) {
            metrics.setLatencyMs(System.currentTimeMillis() - startTime);
            metrics.setSuccess(false);
            if (llmMetricsService != null) llmMetricsService.recordCall(metrics);
            throw new RuntimeException("Gemini Chat failed", e);
        }
    }
}