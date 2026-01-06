package com.purchasingpower.autoflow.agent.tools;

import com.purchasingpower.autoflow.agent.Tool;
import com.purchasingpower.autoflow.agent.ToolCategory;
import com.purchasingpower.autoflow.agent.ToolContext;
import com.purchasingpower.autoflow.agent.ToolResult;
import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.core.SearchResult;
import com.purchasingpower.autoflow.search.SearchService;
import com.purchasingpower.autoflow.search.impl.DefaultSearchOptions;
import com.purchasingpower.autoflow.service.PromptLibraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tool for generating code following repository patterns.
 *
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeGenTool implements Tool {

    private final GeminiClient geminiClient;
    private final SearchService searchService;
    private final PromptLibraryService promptLibrary;

    @Override
    public String getName() {
        return "generate_code";
    }

    @Override
    public String getDescription() {
        return "Generate code following the repository's patterns and conventions. Use after searching for similar code.";
    }

    @Override
    public String getParameterSchema() {
        return "{\"requirement\": \"string (required) - what code to generate\", \"similar_to\": \"string (optional) - search for similar code patterns first\", \"file_type\": \"string (optional: 'class', 'method', 'test', default 'class')\"}";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.ACTION;
    }

    @Override
    public boolean requiresIndexedRepo() {
        return true; // Code generation requires indexed patterns
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String requirement = (String) parameters.get("requirement");
        if (requirement == null || requirement.isBlank()) {
            return ToolResult.failure("requirement parameter is required");
        }

        String similarTo = (String) parameters.get("similar_to");
        String fileType = (String) parameters.getOrDefault("file_type", "class");

        log.info("Generating {} for: {}", fileType, requirement);

        try {
            String existingPatterns = "";
            if (similarTo != null && !similarTo.isBlank()) {
                existingPatterns = findSimilarCode(similarTo, context.getRepositoryIds());
            }

            String prompt = buildCodeGenPrompt(requirement, existingPatterns, fileType);
            String generatedCode = geminiClient.callChatApi(prompt, "CodeGenTool");

            return ToolResult.success(
                Map.of(
                    "requirement", requirement,
                    "fileType", fileType,
                    "code", generatedCode,
                    "usedPatterns", !existingPatterns.isEmpty()
                ),
                "Generated " + fileType + " code"
            );

        } catch (Exception e) {
            log.error("Code generation failed", e);
            return ToolResult.failure("Code generation failed: " + e.getMessage());
        }
    }

    private String findSimilarCode(String query, List<String> repoIds) {
        try {
            DefaultSearchOptions options = DefaultSearchOptions.builder()
                .repositoryIds(repoIds)
                .maxResults(3)
                .build();

            List<SearchResult> results = searchService.search(query, options);

            return results.stream()
                .map(r -> "// From: " + r.getFilePath() + "\n" + (r.getContent() != null ? r.getContent() : ""))
                .collect(Collectors.joining("\n\n"));

        } catch (Exception e) {
            log.warn("Could not find similar code: {}", e.getMessage());
            return "";
        }
    }

    private String buildCodeGenPrompt(String requirement, String existingPatterns, String fileType) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requirement", requirement);
        variables.put("fileType", fileType);
        variables.put("hasExistingPatterns", !existingPatterns.isEmpty());
        variables.put("existingPatterns", existingPatterns);

        return promptLibrary.render("codegen-tool", variables);
    }
}
