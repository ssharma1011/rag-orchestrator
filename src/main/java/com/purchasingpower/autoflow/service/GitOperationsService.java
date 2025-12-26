package com.purchasingpower.autoflow.service;

import java.io.File;
import java.util.List;

public interface GitOperationsService {
    File cloneRepository(String repoUrl, String branchName);

    /**
     * Clone repository to a specific destination directory
     *
     * @param repoUrl Git repository URL (must be clean URL without /tree/ or /blob/)
     * @param branchName Branch to checkout
     * @param destination Target directory for clone
     * @return The destination directory
     */
    File cloneRepository(String repoUrl, String branchName, File destination);

    void commitAndPush(File workspaceDir, String message);

    void createAndCheckoutBranch(File workspaceDir, String newBranchName);

    /**
     * Get current commit hash (HEAD) of repository
     *
     * @param workspaceDir Repository workspace directory
     * @return Git commit SHA (40-character hex string)
     */
    String getCurrentCommitHash(File workspaceDir);

    /**
     * Get list of files changed between two commits
     *
     * @param workspaceDir Repository workspace directory
     * @param fromCommit Starting commit SHA
     * @param toCommit Ending commit SHA (usually HEAD)
     * @return List of relative file paths that changed
     */
    List<String> getChangedFilesBetweenCommits(File workspaceDir, String fromCommit, String toCommit);

    /**
     * Pull latest changes from remote
     * Updates existing workspace to latest commit
     *
     * @param workspaceDir Repository workspace directory
     * @return New commit hash after pull
     */
    String pullLatestChanges(File workspaceDir);

    /**
     * Check if workspace exists and is a valid Git repository
     *
     * @param workspaceDir Directory to check
     * @return true if valid Git repo, false otherwise
     */
    boolean isValidGitRepository(File workspaceDir);

    /**
     * Get repository name from URL
     * Example: "https://github.com/user/repo.git" → "repo"
     *
     * @param repoUrl Git repository URL
     * @return Repository name
     */
    String extractRepoName(String repoUrl);

    /**
     * Extract branch name from GitHub URL
     * Examples:
     *   "https://github.com/user/repo/tree/feature-branch" → "feature-branch"
     *   "https://github.com/user/repo" → null
     *
     * @param repoUrl Git repository URL
     * @return Branch name if present in URL, null otherwise
     */
    String extractBranchFromUrl(String repoUrl);

    /**
     * Get clean repo URL without branch/file paths
     * Examples:
     *   "https://github.com/user/repo/tree/branch" → "https://github.com/user/repo"
     *   "https://github.com/user/repo" → "https://github.com/user/repo"
     *
     * @param repoUrl Git repository URL (may contain /tree/ or /blob/)
     * @return Clean repository URL suitable for git clone
     */
    String getCleanRepoUrl(String repoUrl);
}
