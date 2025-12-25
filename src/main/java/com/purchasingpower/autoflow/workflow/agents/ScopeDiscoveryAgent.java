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

    public Map<String, Object> execute(WorkflowState state) {
        log.info("🔍 Discovering scope for: {}", state.getRequirement());

        RequirementAnalysis req = state.getRequirementAnalysis();
        String repoName = extractRepoName(state.getRepoUrl());

        try {
            List<GraphNode> candidates = findCandidateClasses(req, repoName);

            if (candidates.isEmpty()) {
                Map<String, Object> updates = new HashMap<>(state.toMap());
                updates.put("lastAgentDecision", AgentDecision.askDev(
                    "❌ **No Classes Found**\n\n" +
                    "I couldn't find any classes matching:\n" +
                    "- Domain: " + req.getDomain() + "\n" +
                    "- Requirement: " + req.getSummary() + "\n\n" +
                    "Please verify repo indexing."
                ));
                return updates;
            }

            ScopeProposal proposal = analyzeScope(req, candidates, repoName);
            
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("scopeProposal", proposal);

            int totalFiles = proposal.getTotalFileCount();
            if (totalFiles > MAX_FILES) {
                updates.put("lastAgentDecision", AgentDecision.askDev(
                    "⚠️ **Scope Too Large**\nProposed " + totalFiles + " files. Limit is " + MAX_FILES + "."
                ));
                return updates;
            }

            updates.put("lastAgentDecision", AgentDecision.askDev(proposal.formatForApproval()));
            return updates;

        } catch (Exception e) {
            log.error("Scope discovery failed", e);
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("lastAgentDecision", AgentDecision.error("Failed to discover scope: " + e.getMessage()));
            return updates;
        }
    }

    private List<GraphNode> findCandidateClasses(RequirementAnalysis req, String repoName) {
        Set<GraphNode> candidates = new HashSet<>();

        log.info("════════════════════════════════════════════════════════════");
        log.info("🔍 SCOPE DISCOVERY - Finding Candidate Classes");
        log.info("════════════════════════════════════════════════════════════");
        log.info("📋 Requirement: {}", req.getSummary());
        log.info("🏷️  Extracted Domain: {}", req.getDomain());
        log.info("📦 Repository: {}", repoName);

        // Strategy 1: Domain-based search
        if (req.getDomain() != null && !req.getDomain().isEmpty()) {
            log.info("\n🔎 Strategy 1: Searching by domain '{}'...", req.getDomain());

            List<GraphNode> allNodes = graphRepo.findByRepoName(repoName);
            log.info("   Total nodes in repo: {}", allNodes.size());

            // Log sample of domains to debug
            Map<String, Long> domainCounts = allNodes.stream()
                    .filter(n -> n.getDomain() != null)
                    .collect(java.util.stream.Collectors.groupingBy(
                            GraphNode::getDomain,
                            java.util.stream.Collectors.counting()));
            log.info("   Available domains in repo: {}", domainCounts);

            List<GraphNode> domainClasses = allNodes.stream()
                    .filter(node -> req.getDomain().equalsIgnoreCase(node.getDomain()))
                    .toList();
            log.info("   ✅ Found {} classes with domain '{}'", domainClasses.size(), req.getDomain());

            if (!domainClasses.isEmpty()) {
                log.info("   Sample matches: {}", domainClasses.stream()
                        .limit(5)
                        .map(GraphNode::getSimpleName)
                        .toList());
            }

            candidates.addAll(domainClasses);
        }

        // Strategy 2: Semantic search in Pinecone
        log.info("\n🔎 Strategy 2: Semantic search in Pinecone...");
        log.info("   Embedding requirement: '{}'", req.getSummary());

        List<Double> requirementEmbedding = geminiClient.createEmbedding(req.getSummary());
        log.info("   ✅ Created embedding vector (dimension: {})", requirementEmbedding.size());

        List<PineconeRetriever.CodeContext> semanticMatches =
                pineconeRetriever.findRelevantCodeStructured(requirementEmbedding, repoName);
        log.info("   ✅ Pinecone returned {} semantic matches", semanticMatches.size());

        if (!semanticMatches.isEmpty()) {
            log.info("   Top matches from Pinecone:");
            for (int i = 0; i < Math.min(5, semanticMatches.size()); i++) {
                PineconeRetriever.CodeContext match = semanticMatches.get(i);
                log.info("      {}. {} (score: {}, class: {})",
                        i+1, match.id(), match.score(), match.className());
            }
        }

        int pineconeMatchesFound = 0;
        for (PineconeRetriever.CodeContext match : semanticMatches) {
            Optional<GraphNode> nodeOpt = graphRepo.findByNodeId(match.id());
            if (nodeOpt.isPresent()) {
                candidates.add(nodeOpt.get());
                pineconeMatchesFound++;
            } else {
                log.debug("   ⚠️ Pinecone match '{}' not found in Neo4j", match.id());
            }
        }
        log.info("   ✅ Matched {} Pinecone results to Neo4j nodes", pineconeMatchesFound);

        // Strategy 3: Expand with dependencies
        log.info("\n🔎 Strategy 3: Expanding with dependencies...");
        log.info("   Starting with {} candidates", candidates.size());

        Set<GraphNode> expanded = new HashSet<>(candidates);
        for (GraphNode candidate : candidates) {
            List<String> deps = graphTraversal.findDirectDependencies(candidate.getNodeId(), repoName);
            log.debug("   {} has {} dependencies", candidate.getSimpleName(), deps.size());

            for (String depId : deps) {
                graphRepo.findByNodeId(depId).ifPresent(dep -> {
                    if (req.getDomain() != null && req.getDomain().equals(dep.getDomain())) {
                        expanded.add(dep);
                        log.debug("   Added dependency: {}", dep.getSimpleName());
                    }
                });
            }
        }

        log.info("   ✅ After expansion: {} total candidates", expanded.size());

        log.info("\n📊 FINAL RESULTS:");
        log.info("   Total candidates found: {}", expanded.size());
        if (!expanded.isEmpty()) {
            log.info("   Candidates:");
            expanded.stream()
                    .limit(10)
                    .forEach(node -> log.info("      - {} ({})", node.getSimpleName(), node.getFilePath()));
            if (expanded.size() > 10) {
                log.info("      ... and {} more", expanded.size() - 10);
            }
        }
        log.info("════════════════════════════════════════════════════════════\n");

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
