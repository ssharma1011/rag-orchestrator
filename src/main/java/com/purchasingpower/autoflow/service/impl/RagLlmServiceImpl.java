package com.purchasingpower.autoflow.service.impl;

import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.client.PineconeRetriever;
import com.purchasingpower.autoflow.model.graph.GraphNode;
import com.purchasingpower.autoflow.model.llm.CodeGenerationResponse;
import com.purchasingpower.autoflow.repository.GraphNodeRepository;
import com.purchasingpower.autoflow.service.RagLlmService;
import com.purchasingpower.autoflow.service.graph.GraphTraversalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced RAG service that combines:
 * 1. Vector search (Pinecone) - finds semantically similar code
 * 2. Graph traversal (Oracle) - adds structural dependencies
 * 3. LLM generation (Gemini) - generates code with full context
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagLlmServiceImpl implements RagLlmService {

    private final GeminiClient geminiClient;
    private final PineconeRetriever pineconeRetriever;
    private final GraphTraversalService graphTraversal;
    private final GraphNodeRepository nodeRepository;
    private final PineconeIngestServiceImpl ingestService;

    @Override
    public CodeGenerationResponse generatePatch(String requirements, String repoName, File workspaceDir) {
        log.info("Starting Enhanced RAG workflow for: {}", repoName);

        // ================================================================
        // STEP 1: INDEX REPOSITORY (if needed)
        // ================================================================
        log.info("Step 1: Indexing repository...");
        boolean hasExistingCode = ingestService.ingestRepository(workspaceDir, repoName);

        if (!hasExistingCode) {
            log.info("Empty repository detected. Using scaffold mode.");
            return geminiClient.generateScaffold(requirements, repoName);
        }

        // ================================================================
        // STEP 2: VECTOR SEARCH (Semantic similarity)
        // ================================================================
        log.info("Step 2: Vector search in Pinecone...");
        List<Double> requirementsEmbedding = geminiClient.createEmbedding(requirements);

        // Get top matches from Pinecone
        String pineconeResults = pineconeRetriever.findRelevantCode(requirementsEmbedding, repoName);

        List<String> relevantNodeIds = extractNodeIdsFromPineconeResults(pineconeResults, repoName);
        log.info("Pinecone returned {} relevant classes", relevantNodeIds.size());

        // ================================================================
        // STEP 3: GRAPH EXPANSION (Add dependencies)
        // ================================================================
        log.info("Step 3: Expanding context with graph dependencies...");
        Set<String> expandedContext = new HashSet<>(relevantNodeIds);

        for (String nodeId : relevantNodeIds) {
            // Add dependencies (what this class needs)
            List<String> deps = graphTraversal.findAllDependencies(nodeId, repoName, 2);
            expandedContext.addAll(deps);

            // Add immediate dependents (who uses this class)
            List<String> dependents = graphTraversal.findDirectDependents(nodeId, repoName);
            expandedContext.addAll(dependents);
        }

        log.info("Graph expansion: {} → {} nodes", relevantNodeIds.size(), expandedContext.size());

        // ================================================================
        // STEP 4: FETCH CODE FOR ALL NODES
        // ================================================================
        log.info("Step 4: Fetching code for {} nodes...", expandedContext.size());
        String enhancedContext = buildEnhancedContext(expandedContext, repoName);

        log.info("Final context size: {} characters", enhancedContext.length());

        // ================================================================
        // STEP 5: GENERATE CODE (with full context)
        // ================================================================
        log.info("Step 5: Generating code with enhanced context...");
        CodeGenerationResponse response = geminiClient.generateCodePlan(requirements, enhancedContext);

        log.info("Code generation complete: {} edits, {} tests",
                response.getEdits() != null ? response.getEdits().size() : 0,
                response.getTestsAdded() != null ? response.getTestsAdded().size() : 0);

        return response;
    }

    /**
     * Extract node IDs from Pinecone formatted results.
     * Pinecone returns: "--- MATCH 1 [CLASS] ---\nClass: com.example.Payment..."
     */
    private List<String> extractNodeIdsFromPineconeResults(String results, String repoName) {
        if (results == null || results.isEmpty() || "NO CONTEXT FOUND".equals(results)) {
            return Collections.emptyList();
        }

        Set<String> nodeIds = new HashSet<>();
        String[] lines = results.split("\n");

        for (String line : lines) {
            if (line.startsWith("Class:")) {
                String className = line.substring("Class:".length()).trim();
                if (className.contains("|")) {
                    className = className.substring(0, className.indexOf("|")).trim();
                }
                nodeIds.add(repoName + ":" + className);
            }
        }

        return new ArrayList<>(nodeIds);
    }

    /**
     * Fetch actual code content for all nodes in context.
     */
    private String buildEnhancedContext(Set<String> nodeIds, String repoName) {
        StringBuilder context = new StringBuilder();

        List<GraphNode> nodes = nodeRepository.findByRepoName(repoName).stream()
                .filter(node -> nodeIds.contains(node.getNodeId()))
                .sorted(Comparator.comparing(GraphNode::getType).thenComparing(GraphNode::getSimpleName))
                .collect(Collectors.toList());

        context.append("=== RELEVANT CODE ===\n\n");

        for (GraphNode node : nodes) {
            context.append(String.format("--- %s: %s ---\n",
                    node.getType(), node.getFullyQualifiedName()));

            if (node.getSummary() != null && !node.getSummary().isEmpty()) {
                context.append("Summary: ").append(node.getSummary()).append("\n");
            }

            // Add source code (stored in Pinecone, fetch via node ID)
            String sourceCode = fetchSourceCode(node.getNodeId(), repoName);
            if (sourceCode != null && !sourceCode.isEmpty()) {
                context.append("\n").append(sourceCode).append("\n\n");
            }
        }

        // Add dependency relationships
        context.append("=== DEPENDENCIES ===\n\n");
        for (String nodeId : nodeIds) {
            List<String> deps = graphTraversal.findDirectDependencies(nodeId, repoName);
            if (!deps.isEmpty()) {
                context.append(nodeId).append(" depends on:\n");
                deps.forEach(dep -> context.append("  - ").append(dep).append("\n"));
            }
        }

        return context.toString();
    }

    /**
     * Fetch source code from Pinecone (it's stored in metadata).
     */
    private String fetchSourceCode(String nodeId, String repoName) {
        try {
            // Create a dummy query vector (all zeros) to fetch by ID
            List<Double> dummyVector = Collections.nCopies(768, 0.0);

            // Use Pinecone's fetch by ID (implementation depends on PineconeRetriever)
            // For now, return placeholder
            return "// Source code for " + nodeId + "\n// (Fetched from vector DB)";

        } catch (Exception e) {
            log.warn("Failed to fetch source for {}: {}", nodeId, e.getMessage());
            return null;
        }
    }
}
