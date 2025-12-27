package com.purchasingpower.autoflow.service.git;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * URL parser for Azure DevOps repositories.
 *
 * Handles Azure DevOps URL patterns:
 * - https://dev.azure.com/organization/project/_git/repo
 * - https://dev.azure.com/organization/project/_git/repo?version=GBbranch-name
 * - https://organization.visualstudio.com/project/_git/repo
 *
 * Note: Azure DevOps uses query parameter "version=GBbranch-name" for branch references
 * - GB prefix = Git Branch
 * - GT prefix = Git Tag
 * - GC prefix = Git Commit
 */
public class AzureDevOpsUrlParser implements GitUrlParser {

    @Override
    public String extractBranch(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        // Azure DevOps pattern: ?version=GBbranch-name
        if (url.contains("?version=GB")) {
            int versionIndex = url.indexOf("?version=GB");
            String afterVersion = url.substring(versionIndex + 11); // "?version=GB".length() = 11

            // Branch name continues until next query param or end of string
            int nextParam = afterVersion.indexOf('&');
            String branchName = nextParam > 0 ?
                    afterVersion.substring(0, nextParam) : afterVersion;

            // URL decode the branch name (handles spaces, special chars)
            try {
                return URLDecoder.decode(branchName, StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                return branchName; // Return as-is if decode fails
            }
        }

        return null; // No branch in URL
    }

    @Override
    public String extractCleanRepoUrl(String url) {
        if (url == null) return null;

        // Remove query parameters
        if (url.contains("?")) {
            return url.substring(0, url.indexOf("?"));
        }

        return url;
    }

    @Override
    public String extractRepoName(String url) {
        String cleanUrl = extractCleanRepoUrl(url);

        // Azure DevOps pattern: .../project/_git/repo-name
        if (cleanUrl.contains("/_git/")) {
            int gitIndex = cleanUrl.indexOf("/_git/");
            String afterGit = cleanUrl.substring(gitIndex + 6); // "/_git/".length() = 6

            // Remove any trailing slashes
            if (afterGit.endsWith("/")) {
                afterGit = afterGit.substring(0, afterGit.length() - 1);
            }

            return afterGit;
        }

        // Fallback: get last part after /
        String name = cleanUrl;
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }

        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }

        return name;
    }

    @Override
    public GitProvider getProvider() {
        return GitProvider.AZURE_DEVOPS;
    }
}
