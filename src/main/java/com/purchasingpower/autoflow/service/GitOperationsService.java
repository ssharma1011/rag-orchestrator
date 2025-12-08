package com.purchasingpower.autoflow.service;

import java.io.File;

public interface GitOperationsService {
    File cloneRepository(String repoUrl, String branchName);

    void commitAndPush(File workspaceDir, String message);

    void createAndCheckoutBranch(File workspaceDir, String newBranchName);
}
