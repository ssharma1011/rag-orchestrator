package com.purchasingpower.autoflow.workflow.pipeline;

import com.purchasingpower.autoflow.service.JiraClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineEngine {

    /**
     * Spring automatically injects all beans that implement PipelineStep.
     * They are sorted based on their @Order annotation.
     */
    private final List<PipelineStep> steps;

    private final JiraClientService jiraService;

    /**
     * Entry point for the background automation task.
     *
     * @param issueKey The Jira Ticket ID (e.g., PROJ-123)
     * @return Future (void) to allow the Controller to release the thread.
     */
    @Async("taskExecutor")
    public CompletableFuture<Void> run(String issueKey) {
        log.info("Starting Pipeline Engine for issue: {}", issueKey);

        // 1. Create the Context (The "Box" that travels through the steps)
        PipelineContext context = new PipelineContext(issueKey);

        try {
            // 2. Execute Steps in Order
            for (PipelineStep step : steps) {
                log.info(">> Executing Step: {}", step.getClass().getSimpleName());
                step.execute(context);
            }

            log.info("Pipeline completed successfully for {}", issueKey);

        } catch (Exception e) {
            log.error("Pipeline aborted due to error in step execution", e);
            handleFailure(issueKey, e);
        } finally {
            cleanupWorkspace(context);
        }

        return CompletableFuture.completedFuture(null);
    }

    private void handleFailure(String issueKey, Exception e) {
        try {
            String errorMsg = "❌ **Automation Failed**\n\nError: " + e.getMessage();
            // We subscribe immediately to force the calls since we are already in an Async thread
            jiraService.addComment(issueKey, errorMsg).subscribe();
        } catch (Exception ex) {
            log.error("CRITICAL: Failed to report error to Jira", ex);
        }
    }

    private void cleanupWorkspace(PipelineContext context) {
        if (context.getWorkspaceDir() != null && context.getWorkspaceDir().exists()) {
            try {
                FileSystemUtils.deleteRecursively(context.getWorkspaceDir());
                log.debug("Deleted workspace: {}", context.getWorkspaceDir().getAbsolutePath());
            } catch (Exception e) {
                log.warn("Failed to delete workspace: {}", context.getWorkspaceDir(), e);
            }
        }
    }
}
