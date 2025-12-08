package com.purchasingpower.autoflow.workflow.steps;


import com.purchasingpower.autoflow.configuration.AppProperties;
import com.purchasingpower.autoflow.model.jira.JiraIssueDetails;
import com.purchasingpower.autoflow.service.JiraClientService;
import com.purchasingpower.autoflow.workflow.pipeline.PipelineContext;
import com.purchasingpower.autoflow.workflow.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.Map;


@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class JiraContextLoaderStep implements PipelineStep {

    private final JiraClientService jiraService;
    private final AppProperties appProperties;

    @Override
    public void execute(PipelineContext context) {
        log.info("Step 1: Loading Jira Context for {}", context.getIssueKey());

        JiraIssueDetails issue = jiraService.getIssue(context.getIssueKey()).block();
        context.setJiraIssue(issue);

        String urlId = appProperties.getJira().getFields().getRepoUrlFieldId();
        String nameId = appProperties.getJira().getFields().getRepoNameFieldId();
        String branchId = appProperties.getJira().getFields().getBaseBranchFieldId();
        String acId = appProperties.getJira().getFields().getAcceptanceCriteriaFieldId();


        assert issue != null;
        Map<String, Object> fields = issue.getFields().getCustomFields();

        String repoUrl = (String) fields.get(urlId);
        String repoName = (String) fields.get(nameId);
        String baseBranch = (String) fields.getOrDefault(branchId, "main");

        Object acceptanceCriteriaObject = fields.get(acId);
        String acceptanceCriteria = extractTextFromJiraDoc(acceptanceCriteriaObject);

        if (repoUrl == null) throw new IllegalArgumentException("Missing Repo URL");
        if (repoName == null) throw new IllegalArgumentException("Missing Repo Name");

        context.setRepoUrl(repoUrl);
        context.setRepoName(repoName);
        context.setBaseBranch(baseBranch);
        context.setRequirements(context.getJiraIssue().getFields().getDescriptionText() +
                "\n\nACCEPTANCE CRITERIA:\n" + acceptanceCriteria);
    }

    private String extractTextFromJiraDoc(Object doc) {
        if (doc == null) return "";
        // You need a Jackson ObjectMapper to traverse the "doc" JSON structure
        // This is a complex helper; for now, we will assume it's just a string,
        // but for safety, we pass the raw Object and let the LLM handle it.
        return doc.toString();
    }
}