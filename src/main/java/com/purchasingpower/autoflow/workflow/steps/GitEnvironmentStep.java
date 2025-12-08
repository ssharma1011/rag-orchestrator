package com.purchasingpower.autoflow.workflow.steps;

import com.purchasingpower.autoflow.service.GitOperationsService;
import com.purchasingpower.autoflow.workflow.pipeline.PipelineContext;
import com.purchasingpower.autoflow.workflow.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.File;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class GitEnvironmentStep implements PipelineStep {

    private final GitOperationsService gitService;

    @Override
    public void execute(PipelineContext context) {
        log.info("Step 2: Cloning Repository {}", context.getRepoName());

        File workspace = gitService.cloneRepository(
                context.getRepoUrl(),
                context.getBaseBranch()
        );

        context.setWorkspaceDir(workspace);
    }
}