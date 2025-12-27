package com.purchasingpower.autoflow.service.git;

/**
 * URL parser for GitHub repositories.
 *
 * Handles GitHub URL patterns:
 * - https://github.com/user/repo
 * - https://github.com/user/repo.git
 * - https://github.com/user/repo/tree/branch-name
 * - https://github.com/user/repo/blob/branch-name/path/to/file.java
 */
public class GitHubUrlParser implements GitUrlParser {

    @Override
    public String extractBranch(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        // Pattern: /tree/branch-name or /blob/branch-name
        if (url.contains("/tree/")) {
            int treeIndex = url.indexOf("/tree/");
            String afterTree = url.substring(treeIndex + 6); // "/tree/".length() = 6
            // Remove any trailing path (e.g., /tree/branch/some/file.java)
            int nextSlash = afterTree.indexOf('/');
            return nextSlash > 0 ? afterTree.substring(0, nextSlash) : afterTree;
        }

        if (url.contains("/blob/")) {
            int blobIndex = url.indexOf("/blob/");
            String afterBlob = url.substring(blobIndex + 6);
            int nextSlash = afterBlob.indexOf('/');
            return nextSlash > 0 ? afterBlob.substring(0, nextSlash) : afterBlob;
        }

        return null; // No branch in URL
    }

    @Override
    public String extractCleanRepoUrl(String url) {
        if (url == null) return null;

        // Remove /tree/... or /blob/...
        if (url.contains("/tree/")) {
            return url.substring(0, url.indexOf("/tree/"));
        }
        if (url.contains("/blob/")) {
            return url.substring(0, url.indexOf("/blob/"));
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
        return GitProvider.GITHUB;
    }
}
