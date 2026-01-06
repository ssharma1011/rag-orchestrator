package com.purchasingpower.autoflow.agent.tools;

import com.purchasingpower.autoflow.agent.Tool;
import com.purchasingpower.autoflow.agent.ToolCategory;
import com.purchasingpower.autoflow.agent.ToolContext;
import com.purchasingpower.autoflow.agent.ToolResult;
import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.core.CodeEntity;
import com.purchasingpower.autoflow.knowledge.GraphStore;
import com.purchasingpower.autoflow.service.PromptLibraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tool for explaining code using LLM.
 *
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExplainTool implements Tool {

    private final GraphStore graphStore;
    private final GeminiClient geminiClient;
    private final PromptLibraryService promptLibrary;

    @Override
    public String getName() {
        return "explain_code";
    }

    @Override
    public String getDescription() {
        return "Explain what a class or method does. Provides a human-readable summary of code purpose and behavior.";
    }

    @Override
    public String getParameterSchema() {
        return "{\"entity_id\": \"string (required) - ID of class or method to explain\", \"detail_level\": \"string (optional: 'brief', 'detailed', default 'brief')\"}";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.UNDERSTANDING;
    }

    @Override
    public boolean requiresIndexedRepo() {
        return true; // Explain requires indexed code
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String entityId = (String) parameters.get("entity_id");
        if (entityId == null || entityId.isBlank()) {
            return ToolResult.failure("entity_id parameter is required");
        }

        String detailLevel = (String) parameters.getOrDefault("detail_level", "brief");

        log.info("Explaining entity: {} ({})", entityId, detailLevel);

        try {
            Optional<CodeEntity> entityOpt = graphStore.getEntity(entityId);
            if (entityOpt.isEmpty()) {
                return ToolResult.failure("Entity not found: " + entityId);
            }

            CodeEntity entity = entityOpt.get();
            String prompt = buildExplanationPrompt(entity, detailLevel);
            String explanation = geminiClient.callChatApi(prompt, "ExplainTool");

            return ToolResult.success(
                Map.of(
                    "entityId", entityId,
                    "name", entity.getName(),
                    "type", entity.getType() != null ? entity.getType().name() : "UNKNOWN",
                    "explanation", explanation
                ),
                "Explained " + entity.getName()
            );

        } catch (Exception e) {
            log.error("Explanation failed", e);
            return ToolResult.failure("Failed to explain code: " + e.getMessage());
        }
    }

    private String buildExplanationPrompt(CodeEntity entity, String detailLevel) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("entityType", entity.getType() != null ? entity.getType().name() : "CODE");
        variables.put("fullyQualifiedName", entity.getFullyQualifiedName());
        variables.put("filePath", entity.getFilePath() != null ? entity.getFilePath() : "Unknown");
        variables.put("sourceCode", entity.getSourceCode() != null ? entity.getSourceCode() : "Source not available");
        variables.put("isDetailed", "detailed".equals(detailLevel));

        return promptLibrary.render("explain-tool", variables);
    }
}
