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
    private final GeminiConfig geminiConfig;
    private final ObjectMapper objectMapper;
    private final PromptLibraryService promptLibrary;
    private final LLMMetricsService llmMetricsService;

    private WebClient geminiWebClient;

    @PostConstruct
    public void init() {
        // ✅ SECURITY FIX: Use header-based authentication instead of query parameters
        // Old way: /{version}/models/{model}:{action}?key=API_KEY
        //   - API key appears in server logs (access.log)
        //   - API key appears in proxy logs
        //   - API key appears in browser history
        //   - API key may be cached by intermediaries
        //
        // New way: x-goog-api-key: API_KEY header
        //   - API key only in request headers (not logged by default)
        //   - Not visible in URLs
        //   - More secure according to OAuth 2.0 / REST best practices
        this.geminiWebClient = WebClient.builder()
                .baseUrl(props.getGemini().getBaseUrl())
                .defaultHeader("x-goog-api-key", props.getGemini().getApiKey())  // ✅ Use header instead of query param
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                        .build())
                .build();
    }

    private String getApiUrl(String model, String action) {
        // ✅ SECURITY FIX: Remove API key from URL (now in header)
        return String.format("/%s/models/%s:%s",
                props.getGemini().getApiVersion(), model, action);
    }

    /**
     * Build Retry spec from configuration.
     * Uses exponential backoff with configurable attempts and delays.
     *
     * <p>This method creates a Retry specification based on the GeminiConfig retry settings,
     * allowing retry behavior to be tuned per environment (dev, staging, prod) without code changes.
     * The retry spec uses exponential backoff to progressively increase delay between retries,
     * respecting the configured maximum backoff to prevent excessive wait times.
     *
     * @return configured Retry spec with exponential backoff
     */
    private Retry buildRetrySpec() {
        GeminiConfig.RetryConfig retry = geminiConfig.getRetry();
        return Retry.backoff(
            retry.getMaxAttempts(),
            Duration.ofSeconds(retry.getInitialBackoffSeconds())
        )
        .maxBackoff(Duration.ofSeconds(retry.getMaxBackoffSeconds()))
        .filter(this::isRetryable);
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
                    .retryWhen(buildRetrySpec()).block();

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
                "generationConfig", Map.of("responseMimeType", "application/json", "temperature", geminiConfig.getJsonTemperature()));

        try {
            String json = geminiWebClient.post().uri(url).bodyValue(body)
                    .retrieve().bodyToMono(String.class)
                    .retryWhen(buildRetrySpec()).block();
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

    /**
     * Determines if an exception should trigger a retry based on configuration.
     *
     * <p>This method checks if the throwable is a WebClientResponseException with a status code
     * that matches the configured retryable status codes (e.g., 429, 500, 502, 503, 504).
     * Using configuration allows different retry behavior per environment (dev/staging/prod).
     *
     * @param ex the exception to check
     * @return true if the exception should trigger a retry, false otherwise
     */
    private boolean isRetryable(Throwable ex) {
        if (!(ex instanceof WebClientResponseException webEx)) {
            return false;
        }
        GeminiConfig.RetryConfig retry = geminiConfig.getRetry();
        if (retry.getRetryableStatusCodes() == null) {
            // Fallback to common retryable codes if not configured
            return webEx.getStatusCode().is5xxServerError() ||
                   webEx.getStatusCode().value() == 429;
        }
        return retry.getRetryableStatusCodes().contains(webEx.getStatusCode().value());
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
        var callCtx = com.purchasingpower.autoflow.util.ExternalCallLogger.startCall(
                ServiceType.GEMINI,
                "generateContent",
                log
        );

        String callId = UUID.randomUUID().toString();
        String model = props.getGemini().getChatModel();
        String url = getApiUrl(model, "generateContent");

        callCtx.logRequest("Generating text",
                "Agent", agentName,
                "Conversation", conversationId != null ? conversationId : "N/A",
                "Model", model,
                "Prompt Length", prompt.length() + " chars",
                "Prompt", com.purchasingpower.autoflow.util.ExternalCallLogger.truncate(prompt, 500));

        double temperature = geminiConfig.getTemperatureForAgent(agentName);
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", temperature)
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
                .temperature(temperature)
                .build();

        try {
            String json = geminiWebClient.post().uri(url).bodyValue(body)
                    .retrieve().bodyToMono(String.class)
                    .retryWhen(buildRetrySpec())
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
            int totalTokens = inputTokens + outputTokens;

            // Complete metrics
            metrics.setLatencyMs(latency);
            metrics.setResponse(response);
            metrics.setResponseLength(response.length());
            metrics.setSuccess(true);
            metrics.setInputTokens(inputTokens);
            metrics.setOutputTokens(outputTokens);
            metrics.setTotalTokens(totalTokens);
            metrics.setHttpStatusCode(200);

            double cost = metrics.calculateCost();
            double tokensPerSec = metrics.calculateTokensPerSecond();

            callCtx.logResponse("Text generated successfully",
                    "Tokens", String.format("%d in + %d out = %d total", inputTokens, outputTokens, totalTokens),
                    "Cost", String.format("$%.4f", cost),
                    "Throughput", String.format("%.1f tok/sec", tokensPerSec),
                    "Response Length", response.length() + " chars",
                    "Response", com.purchasingpower.autoflow.util.ExternalCallLogger.truncate(response, 500));

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

            callCtx.logError(e.getStatusCode() + ": " + e.getMessage(), e);

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

            callCtx.logError("Unexpected error", e);

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