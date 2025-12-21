package com.purchasingpower.autoflow.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.configuration.AppProperties;
import com.purchasingpower.autoflow.model.llm.CodeGenerationResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final PromptLibraryService promptLibrary;

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

    public String generateText(String prompt) {
        String model = props.getGemini().getChatModel();
        String url = getApiUrl(model, "generateContent");
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", 0.7));

        try {
            String json = geminiWebClient.post().uri(url).bodyValue(body)
                    .retrieve().bodyToMono(String.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(10)).filter(this::isRetryable)).block();
            JsonNode root = objectMapper.readTree(json);
            return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
        } catch (Exception e) {
            log.error("Text generation failed", e);
            return "Error: " + e.getMessage();
        }
    }

    private boolean isRetryable(Throwable ex) {
        return ex instanceof WebClientResponseException.TooManyRequests || ex instanceof WebClientResponseException.InternalServerError;
    }

    private CodeGenerationResponse parseResponse(String rawJson) throws Exception {
        JsonNode root = objectMapper.readTree(rawJson);
        String content = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
        return objectMapper.readValue(content, CodeGenerationResponse.class);
    }
}