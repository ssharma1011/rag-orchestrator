package com.purchasingpower.autoflow.service.git;

/**
 * Generic Git URL parser for unknown/self-hosted Git providers.
 *
 * Handles basic Git URLs without provider-specific branch extraction:
 * - https://custom-git.company.com/repo.git
 * - git@custom-git.company.com:user/repo.git
 * - ssh://git@custom-git.company.com/repo.git
 *
 * LIMITATION: Cannot extract branch from URL (no standard pattern).
 * User must provide branch explicitly via baseBranch parameter.
 */
public class GenericGitUrlParser implements GitUrlParser {

    @Override
    public String extractBranch(String url) {
        // Generic Git URLs don't have a standard branch pattern
        // Return null - user must provide branch explicitly
        return null;
    }

    @Override
    public String extractCleanRepoUrl(String url) {
        if (url == null) return null;

        // Generic Git URLs are already clean (no provider-specific patterns to remove)
        return url;
    }

    @Override
    public String extractRepoName(String url) {
        if (url == null) return null;

        String name = url;

        // Remove .git suffix
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }

        // Handle SSH URLs: git@host:user/repo → repo
        if (name.contains(":") && name.contains("@")) {
            int colonIndex = name.lastIndexOf(':');
            name = name.substring(colonIndex + 1);
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
        return GitProvider.GENERIC;
    }
}
