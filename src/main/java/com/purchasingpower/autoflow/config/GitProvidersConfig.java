package com.purchasingpower.autoflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Configuration for Git provider URL parsing strategies.
 *
 * <p>Binds git.providers from application.yml to enable support for
 * multiple Git hosting platforms (GitHub, GitLab, Bitbucket, Azure DevOps)
 * without hardcoding provider-specific logic.
 *
 * <p><b>Example configuration (application.yml):</b>
 * <pre>
 * app:
 *   git:
 *     providers:
 *       github:
 *         pattern: "github.com"
 *         branch-separator: "/tree/"
 *         default-branch: "main"
 *       gitlab:
 *         pattern: "gitlab.com"
 *         branch-separator: "/-/tree/"
 *         default-branch: "main"
 * </pre>
 *
 * <p><b>Thread Safety:</b> This is a Spring-managed singleton that is thread-safe.
 *
 * @author AutoFlow Pipeline
 * @since 1.0.0
 */
@ConfigurationProperties("app.git")
@Data
public class GitProvidersConfig {
    private Map<String, ProviderConfig> providers;

    /**
     * Configuration for a single Git provider.
     */
    @Data
    public static class ProviderConfig {
        /**
         * URL pattern to match (e.g., "github.com").
         */
        private String pattern;

        /**
         * Branch separator in web URLs (e.g., "/tree/" for GitHub).
         */
        private String branchSeparator;

        /**
         * Default branch when not specified (e.g., "main").
         */
        private String defaultBranch;
    }

    /**
     * Get configuration for a specific provider by name.
     *
     * @param name Provider name (e.g., "github", "gitlab")
     * @return Provider configuration, or null if not found
     */
    public ProviderConfig getProvider(String name) {
        return providers != null ? providers.get(name) : null;
    }
}
