package com.purchasingpower.autoflow.workflow.agents;

import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.client.PineconeRetriever;
import com.purchasingpower.autoflow.service.GitOperationsService;
import com.purchasingpower.autoflow.service.PromptLibraryService;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DocumentationAgent - Explains codebase without modifying it.
 *
 * Handles read-only queries like:
 * - "Explain what this codebase does"
 * - "Document the payment flow"
 * - "How does authentication work?"
 * - "What design patterns are used?"
 *
 * Uses externalized prompt from: prompts/documentation-agent.yaml
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentationAgent {

    private final PineconeRetriever pineconeRetriever;
    private final GeminiClient geminiClient;
    private final PromptLibraryService promptLibrary;
    private final GitOperationsService gitService;
    private final com.purchasingpower.autoflow.repository.GraphNodeRepository graphNodeRepository;

    public Map<String, Object> execute(WorkflowState state) {
        log.info("📚 Generating documentation for: {}", state.getRequirement());

        try {
            Map<String, Object> updates = new HashMap<>(state.toMap());

            String requirement = state.getRequirement();
            RequirementAnalysis analysis = state.getRequirementAnalysis();

            // ================================================================
            // STEP 1: FIND RELEVANT CODE USING RAG
            // ================================================================

            log.info("🔍 Searching for relevant code...");

            // Create embedding for the question
            List<Double> queryEmbedding = geminiClient.createEmbedding(requirement);

            // Search Pinecone
            String repoName = gitService.extractRepoName(state.getRepoUrl());
            List<PineconeRetriever.CodeContext> relevantCode =
                    pineconeRetriever.findRelevantCodeStructured(queryEmbedding, repoName);

            log.info("Found {} relevant code chunks from Pinecone", relevantCode.size());

            // FALLBACK: If Pinecone returns 0 results, use Oracle CODE_NODES table
            if (relevantCode.isEmpty()) {
                log.warn("⚠️ Pinecone returned 0 results - falling back to Oracle CODE_NODES table");

                List<com.purchasingpower.autoflow.model.graph.GraphNode> graphNodes =
                        graphNodeRepository.findByRepoName(repoName);

                log.info("Found {} code nodes in Oracle for repo: {}", graphNodes.size(), repoName);

                if (!graphNodes.isEmpty()) {
                    // Convert GraphNodes to CodeContext format
                    // Take a representative sample to avoid overwhelming the LLM
                    relevantCode = graphNodes.stream()
                            .limit(20)  // Take top 20 nodes
                            .map(node -> {
                                String className = node.getName();
                                String filePath = node.getFilePath() != null ? node.getFilePath() : "unknown";
                                String methodName = node.getType() == com.purchasingpower.autoflow.model.ast.ChunkType.METHOD ? node.getName() : "";
                                String content = node.getContent() != null ? node.getContent() : "";

                                // Create a representative score based on node type
                                double score = switch (node.getType()) {
                                    case CLASS -> 0.95;
                                    case METHOD -> 0.90;
                                    case FIELD -> 0.85;
                                    default -> 0.80;
                                };

                                return new PineconeRetriever.CodeContext(
                                        className, filePath, methodName, content, score
                                );
                            })
                            .toList();

                    log.info("✅ Converted {} graph nodes to code context", relevantCode.size());
                }
            }

            // ================================================================
            // STEP 2: GENERATE EXPLANATION USING LLM
            // ================================================================

            log.info("🤖 Generating explanation...");

            String prompt = buildPromptFromTemplate(requirement, analysis, relevantCode);

            String explanation = geminiClient.generateText(prompt);

            log.info("✅ Documentation generated ({} characters)", explanation.length());

            // ================================================================
            // STEP 3: RETURN EXPLANATION (NO CODE GENERATION!)
            // ================================================================

            updates.put("documentationResult", explanation);
            updates.put("lastAgentDecision", AgentDecision.endSuccess(
                    "✅ **Documentation Generated**\n\n" + explanation
            ));

            // Mark workflow as complete (skip code generation agents)
            updates.put("workflowStatus", "COMPLETED");

            return updates;

        } catch (Exception e) {
            log.error("Documentation generation failed", e);
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("lastAgentDecision", AgentDecision.error(
                    "Failed to generate documentation: " + e.getMessage()
            ));
            return updates;
        }
    }

    /**
     * Build prompt using externalized template
     */
    private String buildPromptFromTemplate(
            String requirement,
            RequirementAnalysis analysis,
            List<PineconeRetriever.CodeContext> relevantCode) {

        Map<String, Object> variables = new HashMap<>();

        // Basic context
        variables.put("requirement", requirement);
        variables.put("domain", analysis.getDomain() != null ? analysis.getDomain() : "unknown");
        variables.put("taskType", analysis.getTaskType() != null ? analysis.getTaskType() : "documentation");
        variables.put("summary", analysis.getSummary() != null ? analysis.getSummary() : "");

        // Format relevant code for Mustache template
        List<Map<String, Object>> codeData = relevantCode.stream()
                .limit(10)  // Only include top 10 matches
                .map(code -> {
                    Map<String, Object> codeMap = new HashMap<>();
                    codeMap.put("className", code.className());
                    codeMap.put("filePath", code.filePath());
                    codeMap.put("methodName", code.methodName());
                    codeMap.put("score", String.format("%.2f", code.score()));
                    codeMap.put("content", code.content());
                    return codeMap;
                })
                .collect(Collectors.toList());

        variables.put("relevantCode", codeData);

        // Render using PromptLibraryService
        return promptLibrary.render("documentation-agent", variables);
    }
}