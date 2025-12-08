package com.purchasingpower.autoflow.workflow.steps;


import com.purchasingpower.autoflow.service.GitOperationsService;
import com.purchasingpower.autoflow.service.PineconeIngestService;
import com.purchasingpower.autoflow.service.impl.FilePatchServiceImpl;
import com.purchasingpower.autoflow.workflow.pipeline.PipelineContext;
import com.purchasingpower.autoflow.workflow.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
public class PatchApplicationStep implements PipelineStep {

    private final FilePatchServiceImpl patchService;
    private final GitOperationsService gitService;
    private final PineconeIngestService pineconeIngestService;


    @Override
    @SneakyThrows
    public void execute(PipelineContext context) {
        log.info("Step 4: Applying Patches to Disk");

        String newBranch = context.getTargetBranch();
        gitService.createAndCheckoutBranch(context.getWorkspaceDir(), newBranch);

        patchService.applyChanges(
                context.getWorkspaceDir(),
                context.getLlmPlan()
        );

        log.info("Indexing newly generated code into Pinecone...");
        pineconeIngestService.ingestRepository(
                context.getWorkspaceDir(),
                context.getRepoName()
        );

        log.info("Pinecone Index Updated with new changes.");
    }
}