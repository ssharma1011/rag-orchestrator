package com.purchasingpower.autoflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.model.retrieval.RetrievalPlan;
import com.purchasingpower.autoflow.model.retrieval.RetrievalStrategy;
import com.purchasingpower.autoflow.workflow.state.RequirementAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * RetrievalPlanner - Generates dynamic retrieval plans using LLM.
 *
 * Instead of hardcoding "if broad_overview then do X", the LLM analyzes
 * the user's question and generates a custom retrieval plan with multiple strategies.
 *
 * Benefits:
 * - Infinitely flexible (no hardcoded query types)
 * - Self-improving (just tune prompts)
 * - Explainable (LLM explains reasoning)
 * - Scalable to cross-repo queries
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalPlanner {

    private final GeminiClient geminiClient;
    private final PromptLibraryService promptLibrary;
    private final ObjectMapper objectMapper;

    /**
     * Generate a retrieval plan for a given question.
     *
     * @param question User's question
     * @param analysis Requirement analysis from RequirementAnalyzerAgent
     * @param currentRepo Current repository name
     * @return Dynamic retrieval plan
     */
    public RetrievalPlan planRetrieval(String question, RequirementAnalysis analysis, String currentRepo) {
        log.info("🧠 Planning retrieval strategy for: {}", question);

        try {
            // Build prompt using template
            String prompt = buildPlanningPrompt(question, analysis, currentRepo);

            // Get LLM to generate retrieval plan
            String llmResponse = geminiClient.callChatApi(prompt, "retrieval-planner");

            // Parse JSON response
            RetrievalPlan plan = parseRetrievalPlan(llmResponse);

            log.info("📋 Generated plan with {} strategies:", plan.getStrategyCount());
            for (RetrievalStrategy strategy : plan.getStrategies()) {
                log.info("  - {} ({})", strategy.getType(), strategy.getReasoning());
            }

            return plan;

        } catch (Exception e) {
            log.error("Failed to generate retrieval plan, falling back to semantic search", e);
            return createFallbackPlan(question, currentRepo);
        }
    }

    /**
     * Build the planning prompt.
     */
    private String buildPlanningPrompt(String question, RequirementAnalysis analysis, String currentRepo) {
        Map<String, Object> variables = Map.of(
            "question", question,
            "taskType", analysis.getTaskType() != null ? analysis.getTaskType() : "unknown",
            "domain", analysis.getDomain() != null ? analysis.getDomain() : "unknown",
            "summary", analysis.getSummary() != null ? analysis.getSummary() : "",
            "currentRepo", currentRepo
        );

        return promptLibrary.render("retrieval-planner", variables);
    }

    /**
     * Parse LLM response into RetrievalPlan.
     */
    @SuppressWarnings("unchecked")
    private RetrievalPlan parseRetrievalPlan(String llmResponse) throws Exception {
        // Extract JSON from response (LLM might wrap it in markdown)
        String json = extractJson(llmResponse);

        // Parse JSON
        Map<String, Object> planMap = objectMapper.readValue(json, Map.class);

        RetrievalPlan plan = new RetrievalPlan();
        plan.setOverallReasoning((String) planMap.get("overall_reasoning"));
        plan.setEstimatedChunks((Integer) planMap.get("estimated_chunks"));

        // Parse strategies
        List<Map<String, Object>> strategiesData = (List<Map<String, Object>>) planMap.get("strategies");
        if (strategiesData != null) {
            for (Map<String, Object> strategyData : strategiesData) {
                RetrievalStrategy strategy = RetrievalStrategy.builder()
                    .type((String) strategyData.get("type"))
                    .parameters((Map<String, Object>) strategyData.getOrDefault("parameters", Map.of()))
                    .targetRepos((List<String>) strategyData.getOrDefault("target_repos", List.of()))
                    .reasoning((String) strategyData.get("reasoning"))
                    .priority((Integer) strategyData.getOrDefault("priority", 0))
                    .maxResults((Integer) strategyData.get("max_results"))
                    .build();

                plan.addStrategy(strategy);
            }
        }

        return plan;
    }

    /**
     * Extract JSON from LLM response (handles markdown code blocks).
     */
    private String extractJson(String response) {
        // Remove markdown code blocks if present
        String cleaned = response.trim();

        // Check for ```json ... ``` wrapper
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7); // Remove ```json
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3); // Remove ```
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    /**
     * Create fallback plan if LLM planning fails.
     */
    private RetrievalPlan createFallbackPlan(String question, String currentRepo) {
        log.warn("⚠️ Using fallback retrieval plan (semantic search only)");

        RetrievalStrategy semanticSearch = RetrievalStrategy.builder()
            .type("semantic_search")
            .parameters(Map.of(
                "query", question,
                "top_k", 20
            ))
            .targetRepos(List.of(currentRepo))
            .reasoning("Fallback: LLM planning failed")
            .build();

        return RetrievalPlan.builder()
            .strategies(List.of(semanticSearch))
            .overallReasoning("Fallback plan due to LLM planning error")
            .estimatedChunks(20)
            .build();
    }
}
