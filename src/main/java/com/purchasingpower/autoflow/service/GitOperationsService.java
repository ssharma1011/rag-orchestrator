package com.purchasingpower.autoflow.service;

import java.io.File;
import java.util.List;

public interface GitOperationsService {
    File cloneRepository(String repoUrl, String branchName);

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
}
