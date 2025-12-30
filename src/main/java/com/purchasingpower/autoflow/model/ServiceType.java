package com.purchasingpower.autoflow.model;

/**
 * Enumeration of external service types for unified logging.
 *
 * Used by ExternalCallLogger to categorize and log calls to
 * different external services with consistent formatting.
 *
 * @see com.purchasingpower.autoflow.util.ExternalCallLogger
 */
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
