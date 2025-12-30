package com.purchasingpower.autoflow.workflow.agents;

import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.client.PineconeRetriever;
import com.purchasingpower.autoflow.config.AgentConfig;
import com.purchasingpower.autoflow.model.WorkflowStatus;
import com.purchasingpower.autoflow.model.git.ParsedGitUrl;
import com.purchasingpower.autoflow.model.retrieval.CodeContext;
import com.purchasingpower.autoflow.service.GitOperationsService;
import com.purchasingpower.autoflow.service.PromptLibraryService;
import com.purchasingpower.autoflow.util.GitUrlParser;
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
    private final AgentConfig agentConfig;
    private final GitUrlParser gitUrlParser;

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

            // ✅ FIX: Parse URL correctly to extract repo name (handles /tree/branch URLs)
            ParsedGitUrl parsed = gitUrlParser.parse(state.getRepoUrl());
            String repoName = parsed.getRepoName();

            // Search Pinecone
            List<CodeContext> relevantCode =
                    pineconeRetriever.findRelevantCodeStructured(queryEmbedding, repoName);

            log.info("Found {} relevant code chunks from Pinecone", relevantCode.size());

            // Enhanced logging: Show what code was actually retrieved
            if (!relevantCode.isEmpty()) {
                log.info("📋 Retrieved code chunks:");
                relevantCode.stream().limit(agentConfig.getDocumentation().getMaxLogPreview()).forEach(code ->
                    log.info("  - {} (score: {:.2f}, file: {})",
                        code.className(), code.score(), code.filePath())
                );
            } else {
                log.warn("⚠️ Pinecone returned ZERO results for query: {}", requirement);
            }

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
                            .limit(agentConfig.getDocumentation().getMaxFallbackNodes())  // Take top N nodes
                            .map(node -> {
                                String className = node.getSimpleName();  // ✅ Fixed: getSimpleName() not getName()
                                String filePath = node.getFilePath() != null ? node.getFilePath() : "unknown";
                                String methodName = node.getType() == com.purchasingpower.autoflow.model.ast.ChunkType.METHOD ? node.getSimpleName() : "";
                                String content = node.getSummary() != null ? node.getSummary() : "";  // ✅ Fixed: getSummary() not getContent()

                                // Create a representative score based on node type
                                float score = switch (node.getType()) {
                                    case CLASS -> 0.95f;
                                    case METHOD -> 0.90f;
                                    case FIELD -> 0.85f;
                                    default -> 0.80f;
                                };

                                // ✅ Fixed: Correct CodeContext constructor (id, score, chunkType, className, methodName, filePath, content)
                                return new CodeContext(
                                        node.getNodeId(),
                                        score,
                                        node.getType().toString(),
                                        className,
                                        methodName,
                                        filePath,
                                        content
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

            // Log prompt preview for debugging
            log.debug("Generated prompt (first 500 chars): {}",
                prompt.length() > 500 ? prompt.substring(0, 500) + "..." : prompt);

            String explanation = geminiClient.generateText(prompt);

            log.info("✅ Documentation generated ({} characters)", explanation.length());

            // Validate response quality
            validateResponseQuality(explanation, relevantCode);

            // ================================================================
            // STEP 3: RETURN EXPLANATION (NO CODE GENERATION!)
            // ================================================================

            updates.put("documentationResult", explanation);
            updates.put("lastAgentDecision", AgentDecision.endSuccess(
                    "✅ **Documentation Generated**\n\n" + explanation
            ));

            // Mark workflow as complete (skip code generation agents)
            updates.put("workflowStatus", WorkflowStatus.COMPLETED.name());

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
            List<CodeContext> relevantCode) {

        Map<String, Object> variables = new HashMap<>();

        // Basic context
        variables.put("requirement", requirement);
        variables.put("domain", analysis.getDomain() != null ? analysis.getDomain() : "unknown");
        variables.put("taskType", analysis.getTaskType() != null ? analysis.getTaskType() : "documentation");
        variables.put("summary", analysis.getSummary() != null ? analysis.getSummary() : "");

        // Format relevant code for Mustache template
        List<Map<String, Object>> codeData = relevantCode.stream()
                .limit(agentConfig.getDocumentation().getMaxCodeMatches())  // Only include top N matches
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

    /**
     * Validates response quality to detect hallucination.
     *
     * Logs warnings if response appears generic or ungrounded.
     */
    private void validateResponseQuality(String response, List<CodeContext> relevantCode) {
        if (relevantCode.isEmpty()) {
            // No code was provided - response should acknowledge this
            if (!response.contains("No Code Found") &&
                !response.contains("no indexed code") &&
                !response.contains("not yet indexed")) {

                log.warn("⚠️ HALLUCINATION DETECTED: No code provided but response doesn't acknowledge this!");
                log.warn("Response preview: {}", response.substring(0, Math.min(300, response.length())));
            }
        } else {
            // Code was provided - response should reference specific classes/methods
            Set<String> actualClasses = relevantCode.stream()
                    .map(CodeContext::className)
                    .filter(name -> name != null && !name.isEmpty())
                    .collect(Collectors.toSet());

            // Check if response mentions at least one actual class
            boolean mentionsActualCode = actualClasses.stream()
                    .anyMatch(response::contains);

            if (!mentionsActualCode && !actualClasses.isEmpty()) {
                log.warn("⚠️ POSSIBLE HALLUCINATION: Response doesn't mention any retrieved classes");
                log.warn("Expected to see references to: {}", actualClasses);
                log.warn("Response preview: {}", response.substring(0, Math.min(300, response.length())));
            }

            // Check for common hallucinated class names
            String[] commonHallucinations = {
                "UserService", "UserController", "PaymentService", "PaymentController",
                "OrderService", "ProductService", "AuthenticationService", "CustomerService"
            };

            for (String hallucination : commonHallucinations) {
                if (response.contains(hallucination) && !actualClasses.contains(hallucination)) {
                    log.warn("⚠️ HALLUCINATION DETECTED: Response mentions '{}' which is not in provided code", hallucination);
                }
            }
        }
    }
}