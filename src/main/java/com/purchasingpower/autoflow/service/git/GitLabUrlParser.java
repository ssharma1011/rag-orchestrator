package com.purchasingpower.autoflow.service.git;

/**
 * URL parser for GitLab repositories.
 *
 * Handles GitLab URL patterns:
 * - https://gitlab.com/user/repo
 * - https://gitlab.com/user/repo.git
 * - https://gitlab.com/user/repo/-/tree/branch-name
 * - https://gitlab.com/user/repo/-/blob/branch-name/path/to/file.java
 *
 * Note: GitLab uses "/-/" separator before tree/blob
 */
public class GitLabUrlParser implements GitUrlParser {

    @Override
    public String extractBranch(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        // GitLab pattern: /-/tree/branch-name or /-/blob/branch-name
        if (url.contains("/-/tree/")) {
            int treeIndex = url.indexOf("/-/tree/");
            String afterTree = url.substring(treeIndex + 8); // "/-/tree/".length() = 8
            int nextSlash = afterTree.indexOf('/');
            return nextSlash > 0 ? afterTree.substring(0, nextSlash) : afterTree;
        }

        if (url.contains("/-/blob/")) {
            int blobIndex = url.indexOf("/-/blob/");
            String afterBlob = url.substring(blobIndex + 8);
            int nextSlash = afterBlob.indexOf('/');
            return nextSlash > 0 ? afterBlob.substring(0, nextSlash) : afterBlob;
        }

        return null; // No branch in URL
    }

    @Override
    public String extractCleanRepoUrl(String url) {
        if (url == null) return null;

        // Remove /-/tree/... or /-/blob/...
        if (url.contains("/-/tree/")) {
            return url.substring(0, url.indexOf("/-/tree/"));
        }
        if (url.contains("/-/blob/")) {
            return url.substring(0, url.indexOf("/-/blob/"));
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
        return GitProvider.GITLAB;
    }
}
