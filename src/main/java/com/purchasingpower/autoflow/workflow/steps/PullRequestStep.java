package com.purchasingpower.autoflow.workflow.steps;

import com.purchasingpower.autoflow.service.BitbucketService;
import com.purchasingpower.autoflow.service.GitOperationsService;
import com.purchasingpower.autoflow.service.JiraClientService;
import com.purchasingpower.autoflow.workflow.pipeline.PipelineContext;
import com.purchasingpower.autoflow.workflow.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(6)
@RequiredArgsConstructor
public class PullRequestStep implements PipelineStep {

    private final GitOperationsService gitService;
    private final BitbucketService bitbucketService;
    private final JiraClientService jiraService;

    @Override
    public void execute(PipelineContext context) {
        log.info("Step 6: Creating Pull Request");

        // 1. Commit & Push
        String commitMsg = context.getIssueKey() + ": " + context.getJiraIssue().getFields().getSummary();
        gitService.commitAndPush(context.getWorkspaceDir(), commitMsg);

        // 2. Create PR via API
        String prUrl = bitbucketService.createPullRequest(
                context.getRepoName(),
                context.getTargetBranch(),
                context.getBaseBranch(),
                "AI Implementation for " + context.getIssueKey()
        );
        context.setPrUrl(prUrl);

        // 3. Update Jira
        jiraService.addComment(context.getIssueKey(), "✅ **Success!** PR Created: " + prUrl).subscribe();
    }
}