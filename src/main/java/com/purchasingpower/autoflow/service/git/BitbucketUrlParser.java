package com.purchasingpower.autoflow.service.git;

/**
 * URL parser for Bitbucket repositories.
 *
 * Handles Bitbucket URL patterns:
 * - https://bitbucket.org/user/repo
 * - https://bitbucket.org/user/repo.git
 * - https://bitbucket.org/user/repo/src/branch-name
 * - https://bitbucket.org/user/repo/src/branch-name/path/to/file.java
 *
 * Note: Bitbucket uses "/src/" instead of "/tree/"
 */
public class BitbucketUrlParser implements GitUrlParser {

    @Override
    public String extractBranch(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        // Bitbucket pattern: /src/branch-name
        if (url.contains("/src/")) {
            int srcIndex = url.indexOf("/src/");
            String afterSrc = url.substring(srcIndex + 5); // "/src/".length() = 5

            // Remove any trailing path
            int nextSlash = afterSrc.indexOf('/');
            return nextSlash > 0 ? afterSrc.substring(0, nextSlash) : afterSrc;
        }

        return null; // No branch in URL
    }

    @Override
    public String extractCleanRepoUrl(String url) {
        if (url == null) return null;

        // Remove /src/...
        if (url.contains("/src/")) {
            return url.substring(0, url.indexOf("/src/"));
        }

        return url;
    }

    @Override
    public String extractRepoName(String url) {
        String cleanUrl = extractCleanRepoUrl(url);

        String name = cleanUrl;
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }

        // Get last part after /
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }

        return name;
    }

    @Override
    public GitProvider getProvider() {
        return GitProvider.BITBUCKET;
    }
}
