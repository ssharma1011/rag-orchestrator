package com.purchasingpower.autoflow.util;

import com.purchasingpower.autoflow.config.GitProvidersConfig;
import com.purchasingpower.autoflow.model.git.ParsedGitUrl;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for parsing Git provider URLs using configurable Strategy pattern.
 *
 * <p>Handles web URLs with branch references for multiple Git providers:
 * <ul>
 *   <li>GitHub: https://github.com/user/repo/tree/branch</li>
 *   <li>GitLab: https://gitlab.com/user/repo/-/tree/branch</li>
 *   <li>Bitbucket: https://bitbucket.org/workspace/repo/src/branch</li>
 *   <li>Azure DevOps: https://dev.azure.com/org/project/_git/repo?version=GBbranch</li>
 * </ul>
 *
 * <p>Extracts:
 * <ul>
 *   <li>Clean git clone URL</li>
 *   <li>Branch name (if present)</li>
 *   <li>Repository name</li>
 * </ul>
 *
 * <p><b>Design Pattern:</b> Strategy pattern with configuration-driven providers.
 * Adding new Git providers only requires updating application.yml, no code changes needed.
 *
 * @author AutoFlow Pipeline
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitUrlParser {
    private final GitProvidersConfig gitProvidersConfig;
    private List<GitProviderStrategy> strategies;

    /**
     * Initialize provider strategies from configuration.
     *
     * <p>Loads all configured Git providers from application.yml and creates
     * a strategy for each one. This eliminates hardcoded if-else chains and
     * makes the parser extensible.
     */
    @PostConstruct
    public void initStrategies() {
        strategies = new ArrayList<>();

        // Load strategies from configuration
        if (gitProvidersConfig.getProviders() != null) {
            gitProvidersConfig.getProviders().forEach((name, config) -> {
                strategies.add(new ConfigurableGitProviderStrategy(name, config));
            });
        }

        log.info("Loaded {} Git provider strategies: {}",
                strategies.size(),
                gitProvidersConfig.getProviders() != null ?
                        gitProvidersConfig.getProviders().keySet() : "[]");
    }

    /**
     * Parse a Git URL (web or clone URL) and extract components.
     *
     * <p>Uses Strategy pattern to delegate to the appropriate provider
     * based on URL pattern matching.
     *
     * @param url Input URL (can be web URL with branch or clean git URL)
     * @return Parsed components
     * @throws IllegalArgumentException if URL is invalid or provider unsupported
     */
    public ParsedGitUrl parse(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be null or blank");
        }

        String trimmedUrl = url.trim();

        ParsedGitUrl result = strategies.stream()
                .filter(strategy -> strategy.matches(trimmedUrl))
                .findFirst()
                .map(strategy -> strategy.parse(trimmedUrl))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported Git provider URL: " + url +
                        ". Supported providers: " +
                        (gitProvidersConfig.getProviders() != null ?
                                gitProvidersConfig.getProviders().keySet() : "[]")
                ));

        log.debug("Parsed Git URL: {} -> repoUrl={}, branch={}, repoName={}",
                url, result.getRepoUrl(), result.getBranch(), result.getRepoName());

        return result;
    }
}
