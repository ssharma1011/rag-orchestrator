package com.purchasingpower.autoflow.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility for parsing Git provider URLs (GitHub, GitLab, Bitbucket, Azure DevOps).
 *
 * Handles web URLs with branch references:
 * - GitHub: https://github.com/user/repo/tree/branch
 * - GitLab: https://gitlab.com/user/repo/-/tree/branch
 * - Bitbucket: https://bitbucket.org/workspace/repo/src/branch
 * - Azure DevOps: https://dev.azure.com/org/project/_git/repo?version=GBbranch
 *
 * Extracts:
 * - Clean git clone URL
 * - Branch name (if present)
 * - Repository name
 */
@Slf4j
public class GitUrlParser {

    /**
     * Parse a Git URL (web or clone URL) and extract components.
     *
     * @param url Input URL (can be web URL with /tree/ or clean git URL)
     * @return Parsed components
     */
    public static ParsedGitUrl parse(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be null or blank");
        }

        String cleanUrl = url.trim();
        String repoUrl;
        String branch = null;
        String repoName;

        // GitHub: https://github.com/user/repo/tree/branch
        if (cleanUrl.contains("github.com")) {
            if (cleanUrl.contains("/tree/")) {
                int treeIndex = cleanUrl.indexOf("/tree/");
                repoUrl = cleanUrl.substring(0, treeIndex);
                branch = cleanUrl.substring(treeIndex + 6); // Skip "/tree/"
            } else {
                repoUrl = cleanUrl.replaceAll("\\.git$", "");
            }

        // GitLab: https://gitlab.com/user/repo/-/tree/branch
        } else if (cleanUrl.contains("gitlab.com")) {
            if (cleanUrl.contains("/-/tree/")) {
                int treeIndex = cleanUrl.indexOf("/-/tree/");
                repoUrl = cleanUrl.substring(0, treeIndex);
                branch = cleanUrl.substring(treeIndex + 8); // Skip "/-/tree/"
            } else {
                repoUrl = cleanUrl.replaceAll("\\.git$", "");
            }

        // Bitbucket: https://bitbucket.org/workspace/repo/src/branch
        } else if (cleanUrl.contains("bitbucket.org")) {
            if (cleanUrl.contains("/src/")) {
                int srcIndex = cleanUrl.indexOf("/src/");
                repoUrl = cleanUrl.substring(0, srcIndex);
                branch = cleanUrl.substring(srcIndex + 5); // Skip "/src/"
            } else {
                repoUrl = cleanUrl.replaceAll("\\.git$", "");
            }

        // Azure DevOps: https://dev.azure.com/org/project/_git/repo?version=GBbranch
        } else if (cleanUrl.contains("dev.azure.com") || cleanUrl.contains("visualstudio.com")) {
            if (cleanUrl.contains("?version=GB")) {
                int versionIndex = cleanUrl.indexOf("?version=GB");
                repoUrl = cleanUrl.substring(0, versionIndex);
                branch = cleanUrl.substring(versionIndex + 11); // Skip "?version=GB"
            } else {
                repoUrl = cleanUrl.replaceAll("\\.git$", "");
            }

        // Generic git URL (https://domain.com/user/repo.git)
        } else {
            repoUrl = cleanUrl.replaceAll("\\.git$", "");
        }

        // Ensure repoUrl doesn't end with .git
        repoUrl = repoUrl.replaceAll("\\.git$", "");

        // Extract repo name (last path segment)
        repoName = extractRepoName(repoUrl);

        // Default branch if not specified
        if (branch == null || branch.isBlank()) {
            branch = "main";
        }

        // Clean up branch (remove trailing slashes, query params)
        branch = branch.split("[?#]")[0].replaceAll("/$", "");

        log.debug("Parsed Git URL: {} -> repoUrl={}, branch={}, repoName={}",
                url, repoUrl, branch, repoName);

        return new ParsedGitUrl(repoUrl, branch, repoName);
    }

    /**
     * Extract repository name from URL.
     * Example: "https://github.com/user/my-repo" -> "my-repo"
     */
    private static String extractRepoName(String repoUrl) {
        String name = repoUrl;

        // Remove .git suffix
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }

        // Get last path segment
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < name.length() - 1) {
            name = name.substring(lastSlash + 1);
        }

        return name;
    }

    /**
     * Parsed Git URL components.
     */
    @Data
    @AllArgsConstructor
    public static class ParsedGitUrl {
        /**
         * Clean repository URL (without branch references).
         * Example: "https://github.com/user/repo"
         */
        private String repoUrl;

        /**
         * Branch name (defaults to "main" if not specified).
         * Example: "feature/my-branch"
         */
        private String branch;

        /**
         * Repository name (last path segment).
         * Example: "repo"
         */
        private String repoName;
    }
}
