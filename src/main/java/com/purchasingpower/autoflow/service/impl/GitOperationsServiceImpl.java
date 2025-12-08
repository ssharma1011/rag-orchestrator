package com.purchasingpower.autoflow.service.impl;

import com.purchasingpower.autoflow.configuration.AppProperties;
import com.purchasingpower.autoflow.service.GitOperationsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;

import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitOperationsServiceImpl implements GitOperationsService {

    private final AppProperties appProperties;

    @Override
    public File cloneRepository(String repoUrl, String branchName) {
        File destination = new File(
                appProperties.getWorkspaceDir(),
                UUID.randomUUID().toString()
        );

        // Ensure clean state
        if(destination.exists()) {
            FileSystemUtils.deleteRecursively(destination);
        }
        destination.mkdirs();

        Git git = null;
        try {
            log.info("Cloning {} into {}", repoUrl, destination.getName());

            var credentials = new UsernamePasswordCredentialsProvider(
                    appProperties.getBitbucket().getUsername(),
                    appProperties.getBitbucket().getAppPassword()
            );

            // 1. CLONE
            git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(destination)
                    .setCredentialsProvider(credentials)
                    .call();

            // 2. CHECKOUT BASE BRANCH
            String currentBranch = git.getRepository().getBranch();

            if (currentBranch.equals(branchName)) {
                log.info("Already on base branch '{}'. Skipping checkout.", branchName);
            } else {
                log.info("Switching from '{}' to base branch '{}'", currentBranch, branchName);

                // Check if branch exists locally
                boolean branchExists = git.branchList().call().stream()
                        .anyMatch(ref -> ref.getName().endsWith("/" + branchName));

                if (branchExists) {
                    git.checkout().setName(branchName).call();
                } else {
                    // Create local tracking branch
                    git.checkout()
                            .setCreateBranch(true)
                            .setName(branchName)
                            .setStartPoint("origin/" + branchName)
                            .call();
                }
            }

            return destination;

        } catch (Exception e) {
            log.error("Git Clone/Checkout failed. Cleaning up workspace...", e);
            if (git != null) git.close();
            // ✅ FIX: Cleanup immediately on error
            FileSystemUtils.deleteRecursively(destination);
            throw new RuntimeException("Git clone failed: " + e.getMessage(), e);
        } finally {
            if (git != null) git.close();
        }
    }

    @Override
    public void createAndCheckoutBranch(File workspaceDir, String newBranchName) {
        try (Git git = Git.open(workspaceDir)) {
            log.info("Creating and switching to new AI branch: {}", newBranchName);

            // Force create (if it exists, we fail, which is good to avoid overwriting work)
            git.checkout()
                    .setCreateBranch(true)
                    .setName(newBranchName)
                    .call();

        } catch (Exception e) {
            throw new RuntimeException("Failed to create branch " + newBranchName, e);
        }
    }

    @Override
    public void commitAndPush(File workspaceDir, String message) {
        try (Git git = Git.open(workspaceDir)) {
            var credentials = new UsernamePasswordCredentialsProvider(
                    appProperties.getBitbucket().getUsername(),
                    appProperties.getBitbucket().getAppPassword()
            );

            // Add all changes
            git.add().addFilepattern(".").call();

            // Commit
            git.commit().setMessage(message).call();

            // Push current branch
            String currentBranch = git.getRepository().getBranch();
            log.info("Pushing branch '{}' to origin...", currentBranch);

            git.push()
                    .setRemote("origin")
                    .setRefSpecs(new RefSpec(currentBranch + ":" + currentBranch))
                    .setCredentialsProvider(credentials)
                    .call();

            log.info("Push successful.");

        } catch (Exception e) {
            throw new RuntimeException("Git Push Failed: " + e.getMessage(), e);
        }
    }
}