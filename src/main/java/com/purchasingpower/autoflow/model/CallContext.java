package com.purchasingpower.autoflow.model;

import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Context for tracking an external service call with logging support.
 *
 * Provides structured request/response logging with automatic timing,
 * unique call IDs, and consistent formatting across all external services.
 *
 * @see ExternalCallLogger
 * @see ServiceType
 */
public class CallContext {
    private final String callId;
    private final ServiceType service;
    private final String operation;
    private final Instant startTime;
    private final Logger logger;

    public CallContext(ServiceType service, String operation, Logger logger) {
        this.callId = UUID.randomUUID().toString().substring(0, 8);
        this.service = service;
        this.operation = operation;
        this.startTime = Instant.now();
        this.logger = logger;
    }

    public void logRequest(String summary, Object... details) {
        logger.info("{} {} → {} [{}]",
                service.getEmoji(),
                service.getName(),
                operation,
                callId);

        if (summary != null && !summary.isEmpty()) {
            logger.debug("  Request: {}", summary);
        }

        if (details != null && details.length > 0) {
            for (int i = 0; i < details.length; i += 2) {
                if (i + 1 < details.length) {
                    logger.debug("  {}: {}", details[i], details[i + 1]);
                }
            }
        }
    }

    public void logResponse(String summary, Object... details) {
        long durationMs = Duration.between(startTime, Instant.now()).toMillis();

        logger.info("{} {} ← {} [{}] ({}ms)",
                service.getEmoji(),
                service.getName(),
                operation,
                callId,
                durationMs);

        if (summary != null && !summary.isEmpty()) {
            logger.debug("  Response: {}", summary);
        }

        if (details != null && details.length > 0) {
            for (int i = 0; i < details.length; i += 2) {
                if (i + 1 < details.length) {
                    logger.debug("  {}: {}", details[i], details[i + 1]);
                }
            }
        }
    }

    public void logError(String errorMessage, Throwable ex) {
        long durationMs = Duration.between(startTime, Instant.now()).toMillis();

        logger.error("{} {} ✖ {} [{}] ({}ms) - {}",
                service.getEmoji(),
                service.getName(),
                operation,
                callId,
                durationMs,
                errorMessage);

        if (ex != null) {
            logger.debug("  Error details:", ex);
        }
    }

    public String getCallId() {
        return callId;
    }

    public long getElapsedMs() {
        return Duration.between(startTime, Instant.now()).toMillis();
    }
}
