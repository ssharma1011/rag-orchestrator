package com.purchasingpower.autoflow.service.git;

/**
 * Utility for detecting Git provider from repository URL.
 * ENTERPRISE: Supports multiple version control systems.
 */
public class GitProviderDetector {

    /**
     * Detect Git provider from repository URL.
     *
     * @param url Git repository URL
     * @return Detected GitProvider
     */
    public static GitProvider detectProvider(String url) {
        if (url == null || url.trim().isEmpty()) {
            return GitProvider.GENERIC;
        }

        String lowerUrl = url.toLowerCase();

        // GitHub (github.com)
        if (lowerUrl.contains("github.com")) {
            return GitProvider.GITHUB;
        }

        // GitLab (gitlab.com or self-hosted gitlab)
        if (lowerUrl.contains("gitlab.com") || lowerUrl.contains("gitlab")) {
            return GitProvider.GITLAB;
        }

        // Bitbucket (bitbucket.org or self-hosted)
        if (lowerUrl.contains("bitbucket.org") || lowerUrl.contains("bitbucket")) {
            return GitProvider.BITBUCKET;
        }

        // Azure DevOps (dev.azure.com or old visualstudio.com)
        if (lowerUrl.contains("dev.azure.com") ||
            lowerUrl.contains("visualstudio.com") ||
            lowerUrl.contains("azure.com")) {
            return GitProvider.AZURE_DEVOPS;
        }

        // Default to generic Git
        return GitProvider.GENERIC;
    }
}
