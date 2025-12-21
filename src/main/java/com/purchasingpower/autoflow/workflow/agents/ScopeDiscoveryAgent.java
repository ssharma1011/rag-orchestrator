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
                                "Please verify repo indexing."
                );
            }

            // Step 2: LLM decides final scope
            ScopeProposal proposal = analyzeScope(req, candidates, repoName);
            state.setScopeProposal(proposal);

            // Step 3: Validate limits
            int totalFiles = proposal.getTotalFileCount();
            if (totalFiles > MAX_FILES) {
                return AgentDecision.askDev("⚠️ **Scope Too Large**\nProposed " + totalFiles + " files. Limit is " + MAX_FILES + ".");
            }

            return AgentDecision.askDev(proposal.formatForApproval());

        } catch (Exception e) {
            log.error("Scope discovery failed", e);
            return AgentDecision.error("Failed to discover scope: " + e.getMessage());
        }
    }

    private List<GraphNode> findCandidateClasses(RequirementAnalysis req, String repoName) {
        Set<GraphNode> candidates = new HashSet<>();

        // Strategy 1: Domain Search
        if (req.getDomain() != null && !req.getDomain().isEmpty()) {
            List<GraphNode> domainClasses = graphRepo.findByRepoName(repoName).stream()
                    .filter(node -> req.getDomain().equalsIgnoreCase(node.getDomain()))
                    .toList();
            candidates.addAll(domainClasses);
        }

        // Strategy 2: Semantic Search (Pinecone)
        List<Double> requirementEmbedding = geminiClient.createEmbedding(req.getSummary());
        List<PineconeRetriever.CodeContext> semanticMatches =
                pineconeRetriever.findRelevantCodeStructured(requirementEmbedding, repoName);

        for (PineconeRetriever.CodeContext match : semanticMatches) {
            // FIX: Use findByNodeId (String) instead of findById (Long)
            graphRepo.findByNodeId(match.id()).ifPresent(candidates::add);
        }

        // Strategy 3: Dependency Expansion
        Set<GraphNode> expanded = new HashSet<>(candidates);
        for (GraphNode candidate : candidates) {
            List<String> deps = graphTraversal.findDirectDependencies(candidate.getNodeId(), repoName);
            for (String depId : deps) {
                // FIX: Use findByNodeId
                graphRepo.findByNodeId(depId).ifPresent(dep -> {
                    if (req.getDomain() != null && req.getDomain().equals(dep.getDomain())) {
                        expanded.add(dep);
                    }
                });
            }
        }

        return new ArrayList<>(expanded);
    }

    private ScopeProposal analyzeScope(RequirementAnalysis req, List<GraphNode> candidates, String repoName) {
        List<Map<String, Object>> candidateData = candidates.stream()
                .map(node -> Map.of(
                        "className", (Object) node.getSimpleName(),
                        "filePath", node.getFilePath() != null ? node.getFilePath() : "",
                        "purpose", node.getSummary() != null ? node.getSummary() : "",
                        "dependencies", node.getDomain() != null ? node.getDomain() : ""
                ))
                .toList();

        String prompt = promptLibrary.render("scope-discovery", Map.of(
                "requirement", req.getSummary(),
                "domain", req.getDomain() != null ? req.getDomain() : "unknown",
                "candidates", candidateData
        ));

        try {
            String jsonResponse = geminiClient.generateText(prompt);
            ScopeProposalDTO dto = objectMapper.readValue(jsonResponse, ScopeProposalDTO.class);
            return convertToScopeProposal(dto, candidates);
        } catch (Exception e) {
            return createFallbackProposal(candidates);
        }
    }

    private ScopeProposal convertToScopeProposal(ScopeProposalDTO dto, List<GraphNode> candidates) {
        ScopeProposal proposal = ScopeProposal.builder()
                .reasoning(dto.reasoning)
                .estimatedComplexity(dto.estimatedComplexity)
                .risks(dto.risks != null ? dto.risks : new ArrayList<>())
                .build();

        for (String path : dto.filesToModify) {
            GraphNode node = findNodeByPath(path, candidates);
            if (node != null) {
                proposal.getFilesToModify().add(FileAction.builder()
                        .filePath(path).actionType(FileAction.ActionType.MODIFY)
                        .className(node.getFullyQualifiedName()).reason("Required for task").build());
            }
        }
        for (String path : dto.filesToCreate) {
            proposal.getFilesToCreate().add(FileAction.builder()
                    .filePath(path).actionType(FileAction.ActionType.CREATE).reason("New file needed").build());
        }
        for (String path : dto.testsToUpdate) {
            proposal.getTestsToUpdate().add(FileAction.builder()
                    .filePath(path).actionType(FileAction.ActionType.MODIFY).reason("Update tests").build());
        }
        return proposal;
    }

    private GraphNode findNodeByPath(String path, List<GraphNode> candidates) {
        return candidates.stream().filter(n -> path.contains(n.getSimpleName())).findFirst().orElse(null);
    }

    private ScopeProposal createFallbackProposal(List<GraphNode> candidates) {
        ScopeProposal proposal = ScopeProposal.builder().reasoning("Fallback").estimatedComplexity(5).build();
        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            GraphNode node = candidates.get(i);
            proposal.getFilesToModify().add(FileAction.builder()
                    .filePath(node.getFilePath()).actionType(FileAction.ActionType.MODIFY)
                    .className(node.getFullyQualifiedName()).reason("Required").build());
        }
        return proposal;
    }

    private String extractRepoName(String repoUrl) {
        String[] parts = repoUrl.replace(".git", "").split("/");
        return parts[parts.length - 1];
    }

    private static class ScopeProposalDTO {
        public List<String> filesToModify = new ArrayList<>();
        public List<String> filesToCreate = new ArrayList<>();
        public List<String> testsToUpdate = new ArrayList<>();
        public String reasoning;
        public int estimatedComplexity;
        public List<String> risks;
    }
}