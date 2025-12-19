package com.purchasingpower.autoflow.workflow.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.client.PineconeRetriever;
import com.purchasingpower.autoflow.model.graph.GraphNode;
import com.purchasingpower.autoflow.repository.GraphNodeRepository;
import com.purchasingpower.autoflow.service.PromptLibraryService;
import com.purchasingpower.autoflow.service.graph.GraphTraversalService;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AGENT 3: Scope Discovery
 *
 * THE MOST CRITICAL AGENT - Finds ALL affected files automatically.
 *
 * Strategy:
 * 1. Domain search (Oracle graph) ✅ USES KNOWLEDGE GRAPH
 * 2. Semantic search (Pinecone)
 * 3. Dependency expansion (graph traversal) ✅ USES KNOWLEDGE GRAPH
 * 4. LLM filtering (final decision) ✅ USES PROMPT LIBRARY
 *
 * Enforcement: MAX 7 files
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScopeDiscoveryAgent {

    private static final int MAX_FILES = 7;

    private final GraphNodeRepository graphRepo;
    private final GraphTraversalService graphTraversal;
    private final PineconeRetriever pineconeRetriever;
    private final GeminiClient geminiClient;
    private final PromptLibraryService promptLibrary;
    private final ObjectMapper objectMapper;

    public AgentDecision execute(WorkflowState state) {
        log.info("🔍 Discovering scope for: {}", state.getRequirement());

        RequirementAnalysis req = state.getRequirementAnalysis();
        String repoName = extractRepoName(state.getRepoUrl());

        try {
            // Step 1: Find candidate classes using KNOWLEDGE GRAPH
            List<GraphNode> candidates = findCandidateClasses(req, repoName);

            if (candidates.isEmpty()) {
                return AgentDecision.askDev(
                        "❌ **No Classes Found**\n\n" +
                                "I couldn't find any classes matching:\n" +
                                "- Domain: " + req.getDomain() + "\n" +
                                "- Requirement: " + req.getSummary() + "\n\n" +
                                "Please verify:\n" +
                                "1. Is the repo indexed? (Check dashboard)\n" +
                                "2. Is the domain correct?\n" +
                                "3. Did you mean a different module?"
                );
            }

            log.info("Found {} candidate classes from knowledge graph", candidates.size());

            // Step 2: LLM decides final scope (using PROMPT LIBRARY)
            ScopeProposal proposal = analyzeScope(req, candidates, repoName);
            state.setScopeProposal(proposal);

            // Step 3: Validate against MAX_FILES limit
            int totalFiles = proposal.getTotalFileCount();

            if (totalFiles > MAX_FILES) {
                return AgentDecision.askDev(
                        String.format("""
                        ⚠️ **Scope Too Large**
                        
                        This task requires modifying **%d files**.
                        My limit is **%d files** to ensure quality.
                        
                        **Proposed files:**
                        %s
                        
                        **Options:**
                        1. Break into smaller tasks
                        2. Tell me which files are MOST critical (I'll prioritize)
                        3. Proceed anyway (risky - may generate incorrect code)
                        
                        Which option?
                        """,
                                totalFiles,
                                MAX_FILES,
                                formatFileSummary(proposal)
                        )
                );
            }

            // Step 4: Show plan for approval
            return AgentDecision.askDev(proposal.formatForApproval());

        } catch (Exception e) {
            log.error("Scope discovery failed", e);
            return AgentDecision.error("Failed to discover scope: " + e.getMessage());
        }
    }

    /**
     * Find candidate classes using KNOWLEDGE GRAPH + Pinecone
     */
    private List<GraphNode> findCandidateClasses(RequirementAnalysis req, String repoName) {
        Set<GraphNode> candidates = new HashSet<>();

        // ================================================================
        // Strategy 1: KNOWLEDGE GRAPH - Domain Search
        // ================================================================
        if (req.getDomain() != null && !req.getDomain().isEmpty()) {
            // Query Oracle graph by domain metadata
            List<GraphNode> domainClasses = graphRepo.findByRepoName(repoName).stream()
                    .filter(node -> req.getDomain().equalsIgnoreCase(node.getDomain()))
                    .toList();

            candidates.addAll(domainClasses);
            log.info("Knowledge Graph domain search: {} classes in '{}' domain",
                    domainClasses.size(), req.getDomain());
        }

        // ================================================================
        // Strategy 2: Semantic Search (Pinecone)
        // ================================================================
        List<Double> requirementEmbedding = geminiClient.createEmbedding(req.getSummary());
        List<PineconeRetriever.CodeContext> semanticMatches =
                pineconeRetriever.findRelevantCodeStructured(requirementEmbedding, repoName);

        // Convert to GraphNodes
        for (PineconeRetriever.CodeContext match : semanticMatches) {
            graphRepo.findById(match.id()).ifPresent(candidates::add);
        }

        log.info("Semantic search: {} matches", semanticMatches.size());

        // ================================================================
        // Strategy 3: KNOWLEDGE GRAPH - Dependency Expansion
        // ================================================================
        Set<GraphNode> expanded = new HashSet<>(candidates);
        for (GraphNode candidate : candidates) {
            // Use graph traversal to find dependencies
            List<String> deps = graphTraversal.findDirectDependencies(
                    candidate.getNodeId(),
                    repoName
            );

            // Add dependencies if they're in same domain
            for (String depId : deps) {
                graphRepo.findById(depId).ifPresent(dep -> {
                    if (req.getDomain() != null &&
                            req.getDomain().equals(dep.getDomain())) {
                        expanded.add(dep);
                    }
                });
            }
        }

        log.info("After knowledge graph dependency expansion: {} classes", expanded.size());

        return new ArrayList<>(expanded);
    }

    /**
     * Use LLM to decide final scope from candidates
     * USES PROMPT LIBRARY (no hardcoded prompts!)
     */
    private ScopeProposal analyzeScope(
            RequirementAnalysis req,
            List<GraphNode> candidates,
            String repoName) {

        // Prepare candidate data for template
        List<Map<String, Object>> candidateData = candidates.stream()
                .map(node -> Map.of(
                        "className", (Object) node.getSimpleName(),
                        "filePath", node.getFilePath() != null ? node.getFilePath() : "",
                        "purpose", node.getSummary() != null ? node.getSummary() : "",
                        "dependencies", node.getDomain() != null ? node.getDomain() : ""
                ))
                .toList();

        // Render prompt using PROMPT LIBRARY
        String prompt = promptLibrary.render("scope-discovery", Map.of(
                "requirement", req.getSummary(),
                "domain", req.getDomain() != null ? req.getDomain() : "unknown",
                "candidates", candidateData
        ));

        try {
            // Call LLM
            String jsonResponse = geminiClient.generateText(prompt);

            // Parse JSON response
            ScopeProposalDTO dto = objectMapper.readValue(jsonResponse, ScopeProposalDTO.class);

            // Convert DTO to actual ScopeProposal with FileAction objects
            return convertToScopeProposal(dto, candidates);

        } catch (Exception e) {
            log.error("Failed to analyze scope with LLM", e);

            // Fallback: Use first 3 candidates
            return createFallbackProposal(candidates);
        }
    }

    /**
     * Convert DTO from JSON to proper ScopeProposal
     */
    private ScopeProposal convertToScopeProposal(ScopeProposalDTO dto, List<GraphNode> candidates) {
        ScopeProposal proposal = ScopeProposal.builder()
                .reasoning(dto.reasoning)
                .estimatedComplexity(dto.estimatedComplexity)
                .risks(dto.risks != null ? dto.risks : new ArrayList<>())
                .build();

        // Map file paths to FileActions
        Map<String, GraphNode> nodesByPath = candidates.stream()
                .collect(Collectors.toMap(
                        n -> n.getFilePath() != null ? n.getFilePath() : n.getSimpleName(),
                        n -> n,
                        (a, b) -> a
                ));

        // Files to modify
        for (String path : dto.filesToModify) {
            GraphNode node = findNodeByPath(path, candidates);
            if (node != null) {
                proposal.getFilesToModify().add(FileAction.builder()
                        .filePath(path)
                        .actionType(FileAction.ActionType.MODIFY)
                        .className(node.getFullyQualifiedName())
                        .reason("Required for task implementation")
                        .build()
                );
            }
        }

        // Files to create
        for (String path : dto.filesToCreate) {
            proposal.getFilesToCreate().add(FileAction.builder()
                    .filePath(path)
                    .actionType(FileAction.ActionType.CREATE)
                    .reason("New file needed")
                    .build()
            );
        }

        // Tests to update
        for (String path : dto.testsToUpdate) {
            proposal.getTestsToUpdate().add(FileAction.builder()
                    .filePath(path)
                    .actionType(FileAction.ActionType.MODIFY)
                    .reason("Update tests")
                    .build()
            );
        }

        return proposal;
    }

    private GraphNode findNodeByPath(String path, List<GraphNode> candidates) {
        return candidates.stream()
                .filter(n -> path.contains(n.getSimpleName()))
                .findFirst()
                .orElse(null);
    }

    private ScopeProposal createFallbackProposal(List<GraphNode> candidates) {
        ScopeProposal proposal = ScopeProposal.builder()
                .reasoning("Fallback: Using first 3 candidates")
                .estimatedComplexity(5)
                .build();

        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            GraphNode node = candidates.get(i);
            proposal.getFilesToModify().add(FileAction.builder()
                    .filePath(node.getFilePath())
                    .actionType(FileAction.ActionType.MODIFY)
                    .className(node.getFullyQualifiedName())
                    .reason("Required for task")
                    .build()
            );
        }

        return proposal;
    }

    private String formatFileSummary(ScopeProposal proposal) {
        StringBuilder sb = new StringBuilder();

        sb.append("**Modify:**\n");
        proposal.getFilesToModify().forEach(f ->
                sb.append("  - ").append(f.getFilePath()).append("\n")
        );

        sb.append("\n**Create:**\n");
        proposal.getFilesToCreate().forEach(f ->
                sb.append("  - ").append(f.getFilePath()).append("\n")
        );

        return sb.toString();
    }

    private String extractRepoName(String repoUrl) {
        String[] parts = repoUrl.replace(".git", "").split("/");
        return parts[parts.length - 1];
    }

    /**
     * DTO for JSON parsing
     */
    private static class ScopeProposalDTO {
        public List<String> filesToModify;
        public List<String> filesToCreate;
        public List<String> testsToUpdate;
        public String reasoning;
        public int estimatedComplexity;
        public List<String> risks;
    }
}