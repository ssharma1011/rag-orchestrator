package com.purchasingpower.autoflow.util;

/**
 * Strategy interface for Git provider URL parsing.
 *
 * <p>Each Git hosting provider (GitHub, GitLab, Bitbucket, Azure) has different
 * URL patterns for repository links and branch separators. This strategy pattern
 * allows adding new providers without modifying GitUrlParser.
 *
 * <p><b>Example implementations:</b>
 * <ul>
 *   <li>GitHub: https://github.com/user/repo/tree/branch</li>
 *   <li>GitLab: https://gitlab.com/user/repo/-/tree/branch</li>
 *   <li>Bitbucket: https://bitbucket.org/workspace/repo/src/branch</li>
 *   <li>Azure DevOps: https://dev.azure.com/org/project/_git/repo?version=GBbranch</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>
 * GitProviderStrategy strategy = new ConfigurableGitProviderStrategy("github", config);
 * if (strategy.matches(url)) {
 *     ParsedGitUrl result = strategy.parse(url);
 * }
 * </pre>
 *
 * @author AutoFlow Pipeline
 * @since 1.0.0
 */
public interface GitProviderStrategy {
    /**
     * Check if this strategy can handle the given URL.
     *
     * @param url Git URL to check
     * @return true if this strategy can parse the URL, false otherwise
     */
    boolean matches(String url);

    /**
     * Parse Git URL into structured result.
     *
     * @param url Git URL to parse (web URL or clone URL)
     * @return Parsed components (repoUrl, branch, repoName)
     * @throws IllegalArgumentException if URL cannot be parsed
     */
    GitUrlParser.ParsedGitUrl parse(String url);
}
