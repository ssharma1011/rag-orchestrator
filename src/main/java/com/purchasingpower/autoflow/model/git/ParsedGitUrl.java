package com.purchasingpower.autoflow.model.git;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Parsed Git URL components.
 *
 * Extracted from web URLs or clean repository URLs to provide
 * standardized access to repository information.
 *
 * @see com.purchasingpower.autoflow.util.GitUrlParser
 */
@Data
@AllArgsConstructor
public class ParsedGitUrl {
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
