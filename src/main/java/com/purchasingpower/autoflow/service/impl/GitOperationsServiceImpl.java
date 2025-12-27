package com.purchasingpower.autoflow.service.impl;

import com.purchasingpower.autoflow.configuration.AppProperties;
import com.purchasingpower.autoflow.service.GitOperationsService;
import com.purchasingpower.autoflow.service.git.GitProvider;
import com.purchasingpower.autoflow.service.git.GitUrlParser;
import com.purchasingpower.autoflow.service.git.GitUrlParserFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitOperationsServiceImpl implements GitOperationsService {

    private final AppProperties appProperties;

    @Override
    public File cloneRepository(String repoUrl, String branchName) {
        // Generate random destination for backward compatibility
        File destination = new File(
                appProperties.getWorkspaceDir(),
                UUID.randomUUID().toString()
        );
        return cloneRepository(repoUrl, branchName, destination);
    }

    @Override
    public File cloneRepository(String repoUrl, String branchName, File destination) {
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

    @Override
    public String getCurrentCommitHash(File workspaceDir) {
        try (Git git = Git.open(workspaceDir)) {
            ObjectId head = git.getRepository().resolve("HEAD");
            if (head == null) {
                throw new RuntimeException("No HEAD commit found");
            }
            return head.getName();
        } catch (IOException e) {
            log.error("Failed to get current commit hash for: {}", workspaceDir, e);
            throw new RuntimeException("Failed to get commit hash", e);
        }
    }

    @Override
    public List<String> getChangedFilesBetweenCommits(File workspaceDir, String fromCommit, String toCommit) {
        log.info("Getting changed files between {} and {}", fromCommit, toCommit);

        try (Git git = Git.open(workspaceDir)) {
            Repository repository = git.getRepository();

            // Get tree iterators for both commits
            AbstractTreeIterator oldTreeParser = prepareTreeParser(repository, fromCommit);
            AbstractTreeIterator newTreeParser = prepareTreeParser(repository, toCommit);

            // Compute diff
            List<DiffEntry> diffs = git.diff()
                    .setOldTree(oldTreeParser)
                    .setNewTree(newTreeParser)
                    .call();

            // Extract file paths (only ADDED, MODIFIED, COPIED - skip DELETED)
            List<String> changedFiles = diffs.stream()
                    .filter(diff -> diff.getChangeType() != DiffEntry.ChangeType.DELETE)
                    .map(diff -> diff.getNewPath())
                    .filter(path -> path.endsWith(".java"))  // Only Java files
                    .collect(Collectors.toList());

            log.info("Found {} changed Java files", changedFiles.size());
            return changedFiles;

        } catch (IOException | GitAPIException e) {
            log.error("Failed to get changed files", e);
            throw new RuntimeException("Failed to compute Git diff", e);
        }
    }

    @Override
    public String pullLatestChanges(File workspaceDir) {
        log.info("Pulling latest changes for: {}", workspaceDir);

        try (Git git = Git.open(workspaceDir)) {
            // Pull from remote
            git.pull().call();

            // Get new commit hash
            return getCurrentCommitHash(workspaceDir);

        } catch (IOException | GitAPIException e) {
            log.error("Failed to pull latest changes", e);
            throw new RuntimeException("Failed to pull changes", e);
        }
    }

    @Override
    public boolean isValidGitRepository(File workspaceDir) {
        if (workspaceDir == null || !workspaceDir.exists() || !workspaceDir.isDirectory()) {
            return false;
        }

        File gitDir = new File(workspaceDir, ".git");
        return gitDir.exists() && gitDir.isDirectory();
    }

    @Override
    public String extractRepoName(String repoUrl) {
        // ENTERPRISE: Use provider-specific parser
        GitUrlParser parser = GitUrlParserFactory.getParser(repoUrl);
        GitProvider provider = parser.getProvider();

        log.debug("Extracting repo name from {} URL: {}", provider.getDisplayName(), repoUrl);

        return parser.extractRepoName(repoUrl);
    }

    /**
     * Extract branch name from Git URL (provider-agnostic).
     * ENTERPRISE: Supports GitHub, GitLab, Bitbucket, Azure DevOps, etc.
     *
     * Examples:
     *   GitHub:       https://github.com/user/repo/tree/feature-branch → feature-branch
     *   GitLab:       https://gitlab.com/user/repo/-/tree/feature-branch → feature-branch
     *   Bitbucket:    https://bitbucket.org/user/repo/src/feature-branch → feature-branch
     *   Azure DevOps: https://dev.azure.com/org/proj/_git/repo?version=GBfeature-branch → feature-branch
     *   Generic Git:  https://custom-git.company.com/repo.git → null (use default)
     */
    @Override
    public String extractBranchFromUrl(String repoUrl) {
        if (repoUrl == null || repoUrl.trim().isEmpty()) {
            return null;
        }

        // ENTERPRISE: Use provider-specific parser
        GitUrlParser parser = GitUrlParserFactory.getParser(repoUrl);
        GitProvider provider = parser.getProvider();

        String branch = parser.extractBranch(repoUrl);

        if (branch != null) {
            log.debug("Extracted branch '{}' from {} URL", branch, provider.getDisplayName());
        } else {
            log.debug("No branch found in {} URL (will use default)", provider.getDisplayName());
        }

        return branch;
    }

    @Override
    public String getCleanRepoUrl(String repoUrl) {
        if (repoUrl == null) return null;

        // ENTERPRISE: Use provider-specific parser to clean URL
        GitUrlParser parser = GitUrlParserFactory.getParser(repoUrl);
        GitProvider provider = parser.getProvider();

        String cleanUrl = parser.extractCleanRepoUrl(repoUrl);

        if (!cleanUrl.equals(repoUrl)) {
            log.debug("Cleaned {} URL: {} → {}", provider.getDisplayName(), repoUrl, cleanUrl);
        }

        return cleanUrl;
    }

    /**
     * DEPRECATED: Use getCleanRepoUrl() instead.
     * Kept for backward compatibility.
     */
    private String extractCleanRepoUrl(String repoUrl) {
        return getCleanRepoUrl(repoUrl);
    }

    // ================================================================
    // HELPER METHODS
    // ================================================================

    /**
     * Prepare tree parser for diff computation
     */
    private AbstractTreeIterator prepareTreeParser(Repository repository, String commitSha)
            throws IOException {

        try (RevWalk walk = new RevWalk(repository)) {
            ObjectId commitId = repository.resolve(commitSha);
            RevCommit commit = walk.parseCommit(commitId);
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }

            walk.dispose();
            return treeParser;
        }
    }
}