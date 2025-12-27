package com.purchasingpower.autoflow.service.git;

/**
 * Interface for parsing Git URLs from different providers.
 * ENTERPRISE: Provider-agnostic URL parsing for GitHub, GitLab, Bitbucket, Azure DevOps, etc.
 *
 * Each provider has different URL patterns:
 * - GitHub:       https://github.com/user/repo/tree/branch-name
 * - GitLab:       https://gitlab.com/user/repo/-/tree/branch-name
 * - Bitbucket:    https://bitbucket.org/user/repo/src/branch-name
 * - Azure DevOps: https://dev.azure.com/org/project/_git/repo?version=GBbranch-name
 */
public interface GitUrlParser {

    /**
     * Extract branch name from URL.
     *
     * @param url Git repository URL (may contain branch reference)
     * @return Branch name if present in URL, null otherwise
     */
    String extractBranch(String url);

    /**
     * Get clean repository URL without branch/file paths.
     * Removes provider-specific branch/file references to get a clone-able URL.
     *
     * @param url Git repository URL (may contain branch/file references)
     * @return Clean repository URL suitable for git clone
     */
    String extractCleanRepoUrl(String url);

    /**
     * Extract repository name from URL.
     *
     * @param url Git repository URL
     * @return Repository name (last part of URL path)
     */
    String extractRepoName(String url);

    /**
     * Get the Git provider this parser handles.
     *
     * @return GitProvider enum value
     */
    GitProvider getProvider();
}
