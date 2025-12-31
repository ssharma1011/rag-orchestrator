package com.purchasingpower.autoflow.core;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents an indexed code repository.
 *
 * <p>Supports multi-repo scenarios where the platform manages several
 * interconnected repositories (microservices, monolith with frontend, etc.)
 *
 * <p>Example:
 * <pre>
 * Repository repo = Repository.builder()
 *     .id("payment-service")
 *     .url("https://github.com/org/payment-service")
 *     .type(RepositoryType.MICROSERVICE)
 *     .build();
 * </pre>
 *
 * @since 2.0.0
 */
public interface Repository {

    /**
     * Unique identifier for this repository.
     * Typically the repository name (e.g., "payment-service").
     */
    String getId();

    /**
     * Git URL for cloning the repository.
     */
    String getUrl();

    /**
     * Current indexed branch.
     */
    String getBranch();

    /**
     * Repository type for specialized handling.
     */
    RepositoryType getType();

    /**
     * Primary programming language.
     */
    String getLanguage();

    /**
     * Business domain this repository belongs to.
     * Example: "payments", "orders", "inventory"
     */
    String getDomain();

    /**
     * When this repository was last indexed.
     */
    LocalDateTime getLastIndexedAt();

    /**
     * Commit hash that was last indexed.
     */
    String getLastIndexedCommit();

    /**
     * Additional metadata (team ownership, description, etc.)
     */
    Map<String, String> getMetadata();

    /**
     * Repository types for specialized handling.
     */
    enum RepositoryType {
        /**
         * Standalone microservice.
         */
        MICROSERVICE,

        /**
         * Monolithic application with frontend and backend together.
         */
        MONOLITH,

        /**
         * Shared library used by other services.
         */
        LIBRARY,

        /**
         * Frontend-only application (React, Angular, etc.)
         */
        FRONTEND,

        /**
         * Backend-only API service.
         */
        BACKEND_API,

        /**
         * Infrastructure/configuration repository.
         */
        INFRASTRUCTURE
    }
}
