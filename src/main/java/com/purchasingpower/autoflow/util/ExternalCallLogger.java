package com.purchasingpower.autoflow.util;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Unified logging utility for all external service calls (Pinecone, Oracle, Neo4j, LLM).
 * Provides consistent, structured request/response logging.
 */
@Slf4j
public class ExternalCallLogger {

    public enum ServiceType {
        PINECONE("🔵", "Pinecone"),
        ORACLE("🟠", "Oracle"),
        NEO4J("🟢", "Neo4j"),
        GEMINI("🔴", "Gemini"),
        GIT("🔷", "Git");

        private final String emoji;
        private final String name;

        ServiceType(String emoji, String name) {
            this.emoji = emoji;
            this.name = name;
        }

        public String getEmoji() {
            return emoji;
        }

        public String getName() {
            return name;
        }
    }

    public static class CallContext {
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

    public static CallContext startCall(ServiceType service, String operation, Logger logger) {
        return new CallContext(service, operation, logger);
    }

    /**
     * Truncate large strings for logging (to avoid log spam)
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return "(null)";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "... [+" + (text.length() - maxLength) + " chars]";
    }

    /**
     * Format a map for logging
     */
    public static String formatMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        if (map.size() <= 5) {
            return map.toString();
        }
        return "{" + map.size() + " entries}";
    }
}
