package com.purchasingpower.autoflow.agent.interceptors;

import com.purchasingpower.autoflow.agent.Tool;
import com.purchasingpower.autoflow.agent.ToolContext;
import com.purchasingpower.autoflow.agent.ToolInterceptor;
import com.purchasingpower.autoflow.core.impl.RepositoryImpl;
import com.purchasingpower.autoflow.knowledge.GraphStore;
import com.purchasingpower.autoflow.knowledge.IndexingResult;
import com.purchasingpower.autoflow.knowledge.IndexingService;
import com.purchasingpower.autoflow.knowledge.IndexingStatus;
import com.purchasingpower.autoflow.service.ChatStreamService;
import com.purchasingpower.autoflow.service.GitOperationsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interceptor that ensures repository is indexed before code tools execute.
 *
 * Applies to tools that require access to indexed code (search, explain, etc).
 * Automatically triggers indexing if repository hasn't been indexed yet.
 *
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexingInterceptor implements ToolInterceptor {

    private final GraphStore graphStore;
    private final IndexingService indexingService;
    private final GitOperationsService gitService;

    @Value("${app.workspace-dir:/tmp/ai-orchestrator-workspace}")
    private String workspaceDir;

    @Autowired(required = false)
    private ChatStreamService chatStreamService;

    @Override
    public void beforeExecute(Tool tool, ToolContext context) {
        // Only run for tools that need indexed code
        if (!tool.requiresIndexedRepo()) {
            return;
        }

        log.debug("Checking if repo is indexed before executing: {}", tool.getName());

        String repoUrl = context.getRepositoryUrl();

        if (repoUrl == null || repoUrl.isBlank()) {
            log.warn("No repository URL in context for tool: {}", tool.getName());
            return;
        }

        // Check by Repository URL and commit hash
        RepositoryStatus repoStatus = checkRepositoryStatus(repoUrl, context.getBranch());

        if (repoStatus.needsIndexing) {
            log.info("Repository {} indexing. Reason: {}. Auto-indexing before {}",
                repoStatus.exists ? "needs re-" : "not indexed",
                repoStatus.reason,
                tool.getName());

            String conversationId = context.getConversation().getConversationId();
            String repoId = indexRepository(repoUrl, context.getBranch(), conversationId, repoStatus.repositoryId);

            // Update context with the repository ID
            context.getRepositoryIds().clear();
            context.getRepositoryIds().add(repoId);
            log.info("Updated context with repository ID: {}", repoId);
        } else {
            log.info("Repository already indexed and up-to-date with ID: {} (commit: {})",
                repoStatus.repositoryId, repoStatus.lastCommitHash);
            // Update context with existing repository ID
            context.getRepositoryIds().clear();
            context.getRepositoryIds().add(repoStatus.repositoryId);
        }
    }

    @Override
    public boolean appliesTo(Tool tool) {
        // Apply to all tools that require indexed repo
        return tool.requiresIndexedRepo();
    }

    /**
     * Check repository status - whether it needs indexing based on commit hash.
     */
    private RepositoryStatus checkRepositoryStatus(String repoUrl, String branch) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return new RepositoryStatus(false, null, null, true, "No repository URL provided");
        }

        try {
            String normalizedUrl = normalizeRepoUrl(repoUrl);

            // Query Repository node with commit hash
            String cypher = "MATCH (r:Repository) WHERE r.url = $url " +
                           "RETURN r.id as id, r.lastIndexedCommit as commitHash LIMIT 1";
            List<Map<String, Object>> results = graphStore.executeCypherQueryRaw(
                cypher,
                Map.of("url", normalizedUrl)
            );

            if (results.isEmpty()) {
                // Repository not indexed at all
                return new RepositoryStatus(false, null, null, true, "Repository not indexed");
            }

            String repoId = results.get(0).get("id").toString();
            String storedCommitHash = results.get(0).get("commitHash") != null ?
                results.get(0).get("commitHash").toString() : null;

            // Get current commit hash from workspace
            String currentCommitHash = getCurrentCommitHashFromWorkspace(normalizedUrl, branch);

            if (currentCommitHash == null) {
                // Can't determine current hash - assume needs indexing
                return new RepositoryStatus(true, repoId, storedCommitHash, true,
                    "Cannot determine current commit hash");
            }

            if (storedCommitHash == null || !storedCommitHash.equals(currentCommitHash)) {
                // Commit changed or no hash stored - needs re-indexing
                String reason = storedCommitHash == null ?
                    "No commit hash stored (legacy index)" :
                    String.format("Commit changed (stored: %s, current: %s)",
                        storedCommitHash.substring(0, 7), currentCommitHash.substring(0, 7));
                return new RepositoryStatus(true, repoId, currentCommitHash, true, reason);
            }

            // Up to date!
            return new RepositoryStatus(true, repoId, storedCommitHash, false, "Up to date");

        } catch (Exception e) {
            log.error("Failed to check repository status for: {}", repoUrl, e);
            return new RepositoryStatus(false, null, null, true,
                "Error checking status: " + e.getMessage());
        }
    }

    /**
     * Get current commit hash from workspace (clones/pulls if needed).
     */
    private String getCurrentCommitHashFromWorkspace(String repoUrl, String branch) {
        try {
            String repoName = gitService.extractRepoName(repoUrl);
            File workspace = new File(workspaceDir, repoName);

            // Clone or pull to get latest
            if (!gitService.isValidGitRepository(workspace)) {
                log.debug("Cloning repository to check commit hash: {}", repoUrl);
                workspace = gitService.cloneRepository(repoUrl, branch != null ? branch : "main");
            } else {
                log.debug("Pulling latest changes to check commit hash: {}", repoUrl);
                gitService.pullLatestChanges(workspace);
            }

            String commitHash = gitService.getCurrentCommitHash(workspace);
            log.debug("Current commit hash for {}: {}", repoName, commitHash);
            return commitHash;

        } catch (Exception e) {
            log.warn("Failed to get current commit hash for {}: {}", repoUrl, e.getMessage());
            return null;
        }
    }

    /**
         * Repository status DTO.
         */
        private record RepositoryStatus(boolean exists, String repositoryId, String lastCommitHash, boolean needsIndexing,
                                        String reason) {
    }

    private String indexRepository(String repoUrl, String branch, String conversationId, String existingRepoId) {
        try {
            String normalizedUrl = normalizeRepoUrl(repoUrl);

            // Use existing repo ID if available (re-indexing), otherwise create new one
            String repoId = existingRepoId != null ? existingRepoId : UUID.randomUUID().toString();
            boolean isReIndexing = existingRepoId != null;

            if (isReIndexing) {
                log.info("Re-indexing existing repository: {} (ID: {})", repoUrl, repoId);
                sendProgress(conversationId, "Re-indexing repository (cleaning up old data)...");

                // Delete all entities for this repository to avoid duplicates
                deleteRepositoryEntities(repoId);
            } else {
                log.info("Indexing new repository: {}", repoUrl);
                sendProgress(conversationId, "Starting repository indexing...");
            }

            // Get current commit hash
            String currentCommitHash = getCurrentCommitHashFromWorkspace(normalizedUrl, branch);
            log.info("Indexing at commit: {}", currentCommitHash != null ? currentCommitHash.substring(0, 7) : "unknown");

            RepositoryImpl repo = RepositoryImpl.builder()
                .id(repoId)  // Use existing ID for re-indexing
                .url(normalizedUrl)
                .branch(branch != null ? branch : "main")
                .language("Java")
                .lastIndexedCommit(currentCommitHash)  // Store commit hash!
                .build();

            // Start async indexing
            CompletableFuture<IndexingResult> future = indexingService.indexRepositoryAsync(repo);

            // Poll status and send progress updates
            String lastStep = "";
            while (!future.isDone()) {
                try {
                    IndexingStatus status = indexingService.getIndexingStatus(repoId);
                    String currentStep = status.getCurrentStep();

                    if (!currentStep.equals(lastStep)) {
                        sendProgress(conversationId, currentStep + " (" + status.getProgress() + "%)");
                        lastStep = currentStep;
                    }

                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Indexing interrupted", e);
                }
            }

            // Get result
            IndexingResult result = future.get();

            if (result.isSuccess()) {
                sendProgress(conversationId, "Indexing completed: " + result.getEntitiesCreated() + " entities indexed");
                log.info("Successfully indexed {} entities in {}ms (repoId: {})",
                    result.getEntitiesCreated(), result.getDurationMs(), result.getRepositoryId());
                return result.getRepositoryId();
            } else {
                log.error("Indexing failed: {}", result.getErrors());
                throw new RuntimeException("Failed to index repository: " + String.join(", ", result.getErrors()));
            }

        } catch (Exception e) {
            log.error("Auto-indexing failed for: {}", repoUrl, e);
            sendProgress(conversationId, "Indexing failed: " + e.getMessage());
            throw new RuntimeException("Failed to auto-index repository: " + e.getMessage(), e);
        }
    }

    private void deleteRepositoryEntities(String repoId) {
        try {
            log.info("Deleting old entities for repository: {}", repoId);

            // Delete all code entities (Type, Method, Field, Annotation, Package) for this repository
            String cypher = """
                MATCH (e)
                WHERE e.repositoryId = $repoId
                  AND (e:Type OR e:Method OR e:Field OR e:Package OR e:Annotation)
                DETACH DELETE e
                """;

            int deleted = graphStore.executeCypherWrite(cypher, Map.of("repoId", repoId));
            log.info("Deleted {} old entities for repository: {}", deleted, repoId);

        } catch (Exception e) {
            log.warn("Failed to delete old entities for repository {}: {}", repoId, e.getMessage());
            // Don't fail the re-indexing if cleanup fails
        }
    }

    private void sendProgress(String conversationId, String message) {
        if (chatStreamService != null && conversationId != null) {
            log.debug("Sending indexing progress: {}", message);
            chatStreamService.sendThinking(conversationId, message);
        }
    }

    private String normalizeRepoUrl(String url) {
        if (url == null) return null;
        return url.replaceAll("/tree/[^/]+$", "")
                  .replaceAll("/blob/[^/]+$", "")
                  .replaceAll("\\?.*$", "");
    }
}
