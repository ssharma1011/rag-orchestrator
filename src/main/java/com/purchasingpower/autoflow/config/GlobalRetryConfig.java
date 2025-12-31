package com.purchasingpower.autoflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Global retry configuration for transient failures across all agents and services.
 *
 * <p>Provides default retry behavior with exponential backoff for any component that
 * encounters transient failures (network timeouts, temporary service unavailability, etc.).
 * Specific services like Gemini may override these defaults with their own configuration.
 *
 * <p>Properties are loaded from the {@code app.retry} namespace in application.yml.
 * Example configuration:
 * <pre>
 * app:
 *   retry:
 *     max-attempts: 3
 *     backoff-ms: 1000
 *     max-backoff-ms: 10000
 *     multiplier: 2.0
 * </pre>
 *
 * <p><b>Exponential Backoff Calculation:</b>
 * For attempt N (starting at 0), the delay is:
 * <pre>
 *   delay = min(backoff-ms * (multiplier ^ N), max-backoff-ms)
 * </pre>
 * Example with defaults: 1s, 2s, 4s, 8s, then capped at 10s
 *
 * <p><b>Thread Safety:</b> This class is thread-safe as Spring manages a single instance
 * and all fields are effectively immutable after initialization.
 *
 * @author AutoFlow Pipeline
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "app.retry")
@Data
public class GlobalRetryConfig {

    /**
     * Maximum number of retry attempts for transient failures.
     * Total attempts = initial attempt + retries.
     * Range: 1-10
     * Default: 3
     */
    private int maxAttempts;

    /**
     * Initial backoff delay in milliseconds before the first retry.
     * Subsequent retries multiply this value by the multiplier.
     * Default: 1000 (1 second)
     */
    private long backoffMs;

    /**
     * Maximum backoff delay in milliseconds between any two retry attempts.
     * Prevents exponential backoff from growing indefinitely.
     * Default: 10000 (10 seconds)
     */
    private long maxBackoffMs;

    /**
     * Exponential multiplier for backoff calculation.
     * Each retry's delay is multiplied by this factor.
     * Example: backoff=1000, multiplier=2.0 gives: 1s, 2s, 4s, 8s, ...
     * Recommended range: 1.5-3.0
     * Default: 2.0
     */
    private double multiplier;
}
