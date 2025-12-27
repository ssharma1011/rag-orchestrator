package com.purchasingpower.autoflow.service.git;

/**
 * Factory for creating provider-specific Git URL parsers.
 * ENTERPRISE: Supports GitHub, GitLab, Bitbucket, Azure DevOps, and generic Git.
 *
 * Usage:
 * <pre>
 * GitUrlParser parser = GitUrlParserFactory.getParser(repoUrl);
 * String branch = parser.extractBranch(repoUrl);
 * String cleanUrl = parser.extractCleanRepoUrl(repoUrl);
 * String repoName = parser.extractRepoName(repoUrl);
 * </pre>
 */
public class GitUrlParserFactory {

    /**
     * Get appropriate URL parser based on the repository URL.
     *
     * @param url Git repository URL
     * @return Provider-specific GitUrlParser implementation
     */
    public static GitUrlParser getParser(String url) {
        GitProvider provider = GitProviderDetector.detectProvider(url);

        switch (provider) {
            case GITHUB:
                return new GitHubUrlParser();

            case GITLAB:
                return new GitLabUrlParser();

            case BITBUCKET:
                return new BitbucketUrlParser();

            case AZURE_DEVOPS:
                return new AzureDevOpsUrlParser();

            case GENERIC:
            default:
                return new GenericGitUrlParser();
        }
    }

    /**
     * Detect Git provider from URL.
     *
     * @param url Git repository URL
     * @return Detected GitProvider
     */
    public static GitProvider detectProvider(String url) {
        return GitProviderDetector.detectProvider(url);
    }
}
