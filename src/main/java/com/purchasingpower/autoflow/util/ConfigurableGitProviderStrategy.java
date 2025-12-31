package com.purchasingpower.autoflow.util;

import com.purchasingpower.autoflow.config.GitProvidersConfig;
import com.purchasingpower.autoflow.model.git.ParsedGitUrl;
import lombok.RequiredArgsConstructor;

/**
 * Generic configurable Git provider strategy.
 *
 * <p>Uses configuration from application.yml to parse URLs for any Git provider.
 * Eliminates need for provider-specific strategy classes.
 *
 * <p><b>Example:</b>
 * <pre>
 * ProviderConfig config = new ProviderConfig();
 * config.setPattern("github.com");
 * config.setBranchSeparator("/tree/");
 * config.setDefaultBranch("main");
 *
 * GitProviderStrategy strategy = new ConfigurableGitProviderStrategy("github", config);
 * ParsedGitUrl result = strategy.parse("https://github.com/user/repo/tree/develop");
 * // result: repoUrl=https://github.com/user/repo, branch=develop, repoName=repo
 * </pre>
 *
 * <p><b>Supported URL Formats:</b>
 * <ul>
 *   <li>Web URLs with branch: https://github.com/user/repo/tree/branch</li>
 *   <li>Clone URLs: https://github.com/user/repo.git</li>
 *   <li>Clean URLs: https://github.com/user/repo</li>
 *   <li>Azure query params: https://dev.azure.com/org/_git/repo?version=GBbranch</li>
 * </ul>
 *
 * @author AutoFlow Pipeline
 * @since 1.0.0
 */
@RequiredArgsConstructor
public class ConfigurableGitProviderStrategy implements GitProviderStrategy {
    private final String providerName;
    private final GitProvidersConfig.ProviderConfig config;

    @Override
    public boolean matches(String url) {
        return url != null && url.contains(config.getPattern());
    }

    @Override
    public ParsedGitUrl parse(String url) {
        String cleanUrl = url.replace(".git", "").trim();
        String repoUrl;
        String branch;

        if (cleanUrl.contains(config.getBranchSeparator())) {
            int index = cleanUrl.indexOf(config.getBranchSeparator());
            repoUrl = cleanUrl.substring(0, index);
            branch = cleanUrl.substring(index + config.getBranchSeparator().length());

            // Handle Azure's query param format: ?version=GBbranch-name
            if (branch.startsWith("?version=GB")) {
                branch = branch.substring("?version=GB".length());
            }

            // Clean up branch (remove trailing slashes, query params)
            branch = branch.split("[?#]")[0].replaceAll("/$", "");
        } else {
            repoUrl = cleanUrl;
            branch = config.getDefaultBranch();
        }

        String repoName = extractRepoName(repoUrl);
        return new ParsedGitUrl(repoUrl, branch, repoName);
    }

    /**
     * Extract repository name from URL.
     * Example: "https://github.com/user/my-repo" -> "my-repo"
     *
     * @param repoUrl Repository URL
     * @return Repository name (last path segment)
     */
    private String extractRepoName(String repoUrl) {
        String name = repoUrl;

        // Remove .git suffix if present
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
}
