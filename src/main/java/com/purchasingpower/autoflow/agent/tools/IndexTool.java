package com.purchasingpower.autoflow.agent.tools;

import com.purchasingpower.autoflow.agent.Tool;
import com.purchasingpower.autoflow.agent.ToolCategory;
import com.purchasingpower.autoflow.agent.ToolContext;
import com.purchasingpower.autoflow.agent.ToolResult;
import com.purchasingpower.autoflow.core.impl.RepositoryImpl;
import com.purchasingpower.autoflow.knowledge.IndexingResult;
import com.purchasingpower.autoflow.knowledge.IndexingService;
import com.purchasingpower.autoflow.knowledge.IndexingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool for indexing repositories into the knowledge graph.
 *
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexTool implements Tool {

    private final IndexingService indexingService;

    @Override
    public String getName() {
        return "index_repository";
    }

    @Override
    public String getDescription() {
        return "Index a git repository to make it searchable. Parses code structure, extracts entities, and stores in knowledge graph.";
    }

    @Override
    public String getParameterSchema() {
        return "{\"repo_url\": \"string (required) - Git repository URL\", \"branch\": \"string (optional, default 'main')\"}";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.ACTION;
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String repoUrl = (String) parameters.get("repo_url");
        if (repoUrl == null || repoUrl.isBlank()) {
            return ToolResult.failure("repo_url parameter is required");
        }

        String branch = (String) parameters.getOrDefault("branch", "main");

        log.info("Indexing repository: {} (branch: {})", repoUrl, branch);

        try {
            RepositoryImpl repo = RepositoryImpl.builder()
                .url(repoUrl)
                .branch(branch)
                .language("Java")
                .build();

            IndexingResult result = indexingService.indexRepository(repo);

            if (result.isSuccess()) {
                return ToolResult.success(
                    Map.of(
                        "success", true,
                        "entitiesCreated", result.getEntitiesCreated(),
                        "relationshipsCreated", result.getRelationshipsCreated(),
                        "durationMs", result.getDurationMs()
                    ),
                    "Indexed " + result.getEntitiesCreated() + " entities in " + result.getDurationMs() + "ms"
                );
            } else {
                return ToolResult.failure("Indexing failed: " + String.join(", ", result.getErrors()));
            }

        } catch (Exception e) {
            log.error("Indexing failed", e);
            return ToolResult.failure("Indexing failed: " + e.getMessage());
        }
    }

    public ToolResult getStatus(String repositoryId) {
        IndexingStatus status = indexingService.getIndexingStatus(repositoryId);
        return ToolResult.success(
            Map.of(
                "repositoryId", status.getRepositoryId(),
                "state", status.getState().name(),
                "progress", status.getProgress(),
                "currentStep", status.getCurrentStep()
            ),
            "Indexing status: " + status.getState()
        );
    }
}
