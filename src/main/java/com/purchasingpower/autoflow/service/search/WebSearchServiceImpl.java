package com.purchasingpower.autoflow.service.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Web search using Tavily API.
 *
 * <p>Tavily is optimized for AI agents - returns concise answers not just links.
 * Perfect for finding latest library APIs when compilation fails.
 *
 * <p><b>Why Tavily:</b> Returns AI-synthesized answers saving tokens and improving accuracy.
 *
 * @since 1.0.0
 */
@Service
@Slf4j
@EnableConfigurationProperties(WebSearchServiceImpl.WebSearchConfig.class)
public class WebSearchServiceImpl implements WebSearchService {

    private final WebSearchConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebSearchServiceImpl(WebSearchConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled()
            && config.getApiKey() != null
            && !config.getApiKey().isEmpty();
    }

    @Override
    public SearchResult search(String query) {
        Preconditions.checkNotNull(query, "Query cannot be null");
        Preconditions.checkArgument(!query.isEmpty(), "Query cannot be empty");

        if (!isEnabled()) {
            log.debug("Web search disabled");
            return buildEmptyResult(query);
        }

        log.info("🔍 Searching Tavily: {}", query);
        long startTime = System.currentTimeMillis();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.tavily.com/search"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(buildTavilyRequest(query)))
                    .build();

            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                log.warn("Tavily status {}: {}", response.statusCode(), response.body());
                return buildEmptyResult(query);
            }

            return parseResponse(query, response.body(), System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            // Graceful degradation - don't fail pipeline
            log.warn("Tavily failed for '{}': {}", query, e.getMessage());
            return buildEmptyResult(query);
        }
    }

    @Override
    public SearchResult searchLibraryAPI(String libraryName, String apiName) {
        Preconditions.checkNotNull(libraryName, "Library name cannot be null");
        Preconditions.checkNotNull(apiName, "API name cannot be null");

        // Include current year for latest docs
        return search(String.format(
            "%s %s Java API official documentation 2024 2025", 
            libraryName, apiName
        ));
    }

    private String buildTavilyRequest(String query) {
        String escaped = query.replace("\"", "\\\"").replace("\n", "\\n");
        return String.format("""
                {"api_key":"%s","query":"%s","search_depth":"basic","include_answer":true,"max_results":%d}
                """, config.getApiKey(), escaped, config.getMaxResults());
    }

    private SearchResult parseResponse(String query, String body, long timeMs) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        String answer = root.has("answer") ? root.get("answer").asText() : "";
        List<String> sources = extractSources(root);
        int confidence = calculateConfidence(answer, sources);

        log.info("✅ {}% confidence from {} sources ({}ms)", confidence, sources.size(), timeMs);

        return SearchResult.builder()
                .query(query)
                .answer(answer)
                .confidence(confidence)
                .sources(sources)
                .searchTimeMs(timeMs)
                .build();
    }

    private List<String> extractSources(JsonNode root) {
        List<String> sources = new ArrayList<>();
        if (root.has("results")) {
            for (JsonNode result : root.get("results")) {
                if (result.has("url")) sources.add(result.get("url").asText());
            }
        }
        return sources;
    }

    private int calculateConfidence(String answer, List<String> sources) {
        if (answer == null || answer.trim().isEmpty()) return 0;

        int conf = 40;
        if (answer.length() > 200) conf += 20;
        if (answer.length() > 500) conf += 10;
        conf += Math.min(sources.size() * 5, 20);
        
        // Boost for official sources
        long official = sources.stream()
                .filter(u -> u.contains("github.com") || u.contains("docs.") || 
                            u.contains("apache.org") || u.contains("spring.io"))
                .count();
        conf += (int) (official * 5);
        
        // Boost for code examples
        if (answer.contains("```") || answer.contains("import ") || answer.contains("@")) {
            conf += 10;
        }
        
        return Math.min(conf, 95);
    }

    private SearchResult buildEmptyResult(String query) {
        return SearchResult.builder()
                .query(query)
                .answer("")
                .confidence(0)
                .sources(List.of())
                .searchTimeMs(0)
                .build();
    }

    @Data
    @ConfigurationProperties("app.web-search")
    public static class WebSearchConfig {
        private boolean enabled = false;
        private String provider = "tavily";
        private String apiKey;
        private int maxResults = 5;
        private int timeoutSeconds = 10;
        private List<String> triggerPatterns = List.of();
    }
}
