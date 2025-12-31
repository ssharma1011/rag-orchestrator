package com.purchasingpower.autoflow.util;

import com.purchasingpower.autoflow.model.CallContext;
import com.purchasingpower.autoflow.model.ServiceType;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Unified logging utility for all external service calls (Oracle, Neo4j, LLM).
 * Provides consistent, structured request/response logging.
 */
@Slf4j
public class ExternalCallLogger {

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
