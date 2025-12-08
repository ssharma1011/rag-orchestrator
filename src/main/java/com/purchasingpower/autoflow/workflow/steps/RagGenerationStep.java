package com.purchasingpower.autoflow.workflow.steps;


import com.purchasingpower.autoflow.model.llm.CodeGenerationResponse;
import com.purchasingpower.autoflow.service.RagLlmService;
import com.purchasingpower.autoflow.workflow.pipeline.PipelineContext;
import com.purchasingpower.autoflow.workflow.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class RagGenerationStep implements PipelineStep {

    private final RagLlmService llmService;

    @Override
    public void execute(PipelineContext context) {
        log.info("Step 3: Generating Code via Gemini Pro");

        String requirements = context.getJiraIssue().getFields().getDescriptionText();
        String repoName = context.getRepoName();

        CodeGenerationResponse plan = llmService.generatePatch(requirements, repoName, context.getWorkspaceDir());
        context.setLlmPlan(plan);

        context.setTargetBranch(plan.getBranchName());
        log.info("LLM generated {} edits and {} tests",
                plan.getEdits().size(),
                plan.getTestsAdded().size());
    }
}