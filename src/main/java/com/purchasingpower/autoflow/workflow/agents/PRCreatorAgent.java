package com.purchasingpower.autoflow.workflow.agents;

import com.purchasingpower.autoflow.model.WorkflowStatus;
import com.purchasingpower.autoflow.service.BitbucketService;
import com.purchasingpower.autoflow.service.GitOperationsService;
import com.purchasingpower.autoflow.util.GitUrlParser;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PRCreatorAgent {

    private final GitOperationsService gitService;
    private final BitbucketService bitbucketService;
    private final GitUrlParser gitUrlParser;

    public Map<String, Object> execute(WorkflowState state) {
        log.info("🚀 Creating PR...");

        try {
            String branchName = "autoflow/" + state.getConversationId();
            
            // FIX: Use createAndCheckoutBranch (not createBranch)
            gitService.createAndCheckoutBranch(state.getWorkspaceDir(), branchName);
            
            String commitMessage = "AutoFlow: " + state.getRequirement();
            
            // FIX: Use commitAndPush (not commitChanges + pushBranch separately)
            gitService.commitAndPush(state.getWorkspaceDir(), commitMessage);
            
            log.info("Pushed commits to: {}", branchName);

            // Create PR - FIX: Use correct parameter order
            // ✅ FIX: Parse URL correctly to extract repo name (handles /tree/branch URLs)
            String repoName = gitUrlParser.parse(state.getRepoUrl()).getRepoName();
            String baseBranch = state.getBaseBranch() != null ? state.getBaseBranch() : "develop";
            
            String prUrl = bitbucketService.createPullRequest(
                    repoName,
                    branchName,
                    baseBranch,
                    state.getPrDescription()  // title parameter
            );

            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("prUrl", prUrl);
            updates.put("workflowStatus", WorkflowStatus.COMPLETED.name());

            log.info("✅ Pull request created: {}", prUrl);
            
            updates.put("lastAgentDecision", AgentDecision.proceed("PR created successfully"));
            return updates;

        } catch (Exception e) {
            log.error("Failed to create PR", e);
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("lastAgentDecision", AgentDecision.error("PR creation failed: " + e.getMessage()));
            return updates;
        }
    }
}
