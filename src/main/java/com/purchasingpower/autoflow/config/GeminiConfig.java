package com.purchasingpower.autoflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Configuration for Google Gemini API integration.
 *
 * <p>This configuration manages settings for the Gemini chat and embedding models,
 * including temperature settings per agent type and retry behavior for API calls.
 *
 * <p>Properties are loaded from the {@code app.gemini} namespace in application.yml.
 * Example configuration:
 * <pre>
 * app:
 *   gemini:
 *     api-key: ${GEMINI_KEY}
 *     chat-model: gemini-flash-latest
 *     embedding-model: text-embedding-004
 *     default-temperature: 0.7
 *     json-temperature: 0.2
 *     agent-temperatures:
 *       code-generator: 0.3
 *       documentation: 0.8
 *     retry:
 *       max-attempts: 3
 *       initial-backoff-seconds: 2
 * </pre>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe as Spring manages a single instance
 * and all fields are effectively immutable after initialization.
 *
 * @author AutoFlow Pipeline
 * @since 1.0.0
 */
@Component
@ConfigurationProperties(prefix = "app.gemini")
@Data
public class GeminiConfig {

    /**
     * API key for Google Gemini service. Should be set via environment variable GEMINI_KEY.
     */
    private String apiKey;

    /**
     * The chat model to use for conversational AI tasks.
     * Example: "gemini-flash-latest"
     */
    private String chatModel;

    /**
     * The embedding model to use for vectorizing text.
     * Example: "text-embedding-004"
     */
    private String embeddingModel;

    /**
     * Default temperature for Gemini API calls.
     * Range: 0.0 (deterministic) to 1.0 (creative)
     * Default: 0.7
     */
    private double defaultTemperature;

    /**
     * Temperature specifically for JSON/structured output generation.
     * Lower values (0.0-0.3) ensure more consistent JSON format.
     * Default: 0.2
     */
    private double jsonTemperature;

    /**
     * Agent-specific temperature overrides.
     * If an agent is not in this map, defaultTemperature is used.
     * Example: {"code-generator": 0.3, "documentation": 0.8}
     */
    private Map<String, Double> agentTemperatures;

    /**
     * Retry configuration for transient Gemini API failures.
     */
    private RetryConfig retry;

    /**
     * Get the temperature setting for a specific agent.
     *
     * <p>This method allows agents to request their specific temperature setting,
     * falling back to the default temperature if the agent has no specific override.
     *
     * @param agentName the name of the agent (e.g., "code-generator", "documentation")
     * @return the temperature for this agent, or defaultTemperature if not configured
     */
    public double getTemperatureForAgent(String agentName) {
        if (agentTemperatures == null) {
            return defaultTemperature;
        }
        return agentTemperatures.getOrDefault(agentName, defaultTemperature);
    }

    /**
     * Retry configuration for Gemini API calls.
     *
     * <p>Defines exponential backoff behavior for transient failures like rate limits
     * and temporary server errors.
     */
    @Data
    public static class RetryConfig {

        /**
         * Maximum number of retry attempts for failed API calls.
         * Minimum recommended value: 1, Maximum: 10
         */
        private int maxAttempts;

        /**
         * Initial backoff delay in seconds before the first retry.
         * Subsequent retries multiply this by the multiplier factor.
         */
        private long initialBackoffSeconds;

        /**
         * Maximum backoff delay in seconds between retries.
         * Prevents indefinite growth of backoff delays.
         */
        private long maxBackoffSeconds;

        /**
         * Multiplier for exponential backoff calculation.
         * Example: initialBackoff=2, multiplier=2.0 gives delays: 2s, 4s, 8s, etc.
         */
        private double multiplier;

        /**
         * HTTP status codes that should trigger a retry.
         * Common values: 429 (rate limit), 500, 502, 503, 504 (server errors)
         */
        private List<Integer> retryableStatusCodes;
    }
}
