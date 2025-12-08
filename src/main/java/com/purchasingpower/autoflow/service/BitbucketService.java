package com.purchasingpower.autoflow.service;

public interface BitbucketService {

    /**
     * @param repoSlug The repository name (e.g. "payment-service")
     * @param sourceBranch The branch with the new code
     * @param destinationBranch The branch to merge into (e.g. "develop")
     * @param title The PR title
     * @return The URL of the created PR
     */
    String createPullRequest(String repoSlug, String sourceBranch, String destinationBranch, String title);
}
