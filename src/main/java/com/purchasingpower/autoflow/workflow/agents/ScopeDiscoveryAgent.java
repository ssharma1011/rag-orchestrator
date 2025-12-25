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

    // Track method-level matches from Pinecone
    // Key: className, Value: List of (methodName, score, content)
    private final Map<String, List<MethodMatch>> methodMatches = new HashMap<>();

    private record MethodMatch(String methodName, float score, String content, String chunkType) {}

    private List<GraphNode> findCandidateClasses(RequirementAnalysis req, String repoName) {
        Set<GraphNode> candidates = new HashSet<>();
        methodMatches.clear(); // Reset for each execution

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

        // CRITICAL FILTER: Use adaptive threshold based on score distribution
        // This prevents irrelevant classes while adapting to different codebases
        final int MAX_CLASSES = 3;  // Limit to top 3 most relevant classes
        final int MAX_CHUNKS = 10;   // Process max 10 chunks

        // Calculate adaptive threshold using score gap analysis
        double adaptiveThreshold = calculateAdaptiveThreshold(semanticMatches);

        List<PineconeRetriever.CodeContext> filteredMatches = semanticMatches.stream()
                .filter(m -> m.score() >= adaptiveThreshold)
                .limit(MAX_CHUNKS)
                .toList();

        log.info("   🔍 Adaptive threshold: {:.3f} (analyzed {} matches)",
                adaptiveThreshold, semanticMatches.size());
        log.info("   🔍 Filtered to {} high-relevance matches", filteredMatches.size());

        int pineconeMatchesFound = 0;
        Set<String> processedClasses = new HashSet<>();  // Track unique classes

        for (PineconeRetriever.CodeContext match : filteredMatches) {
            // Pinecone stores METHOD/FIELD chunks with IDs like:
            // "repo:com.package.ClassName.methodName"
            // Extract class name from chunk ID (PineconeRetriever may not populate className field)

            String className = match.className();
            if (className == null || className.isEmpty()) {
                // Fallback: extract from chunk ID
                className = extractClassNameFromChunkId(match.id());
            }

            String methodName = match.methodName();  // Method name if this is a method chunk
            String chunkType = match.chunkType();     // METHOD, FIELD, CLASS, etc.

            // Skip if we already processed this class
            if (className != null && processedClasses.contains(className)) {
                // Track additional methods - let LLM decide relevance based on knowledge graph
                // The Neo4j context (call graphs, signatures, purposes) helps LLM reason
                // which methods actually need modification
                if (methodName != null && !methodName.isEmpty()) {
                    methodMatches.computeIfAbsent(className, k -> new ArrayList<>())
                            .add(new MethodMatch(methodName, match.score(), match.content(), chunkType));
                    log.debug("   📌 Tracked method '{}' for class '{}' (score: {:.2f}) - LLM will decide relevance",
                            methodName, className.substring(className.lastIndexOf('.') + 1), match.score());
                }
                continue;
            }

            if (processedClasses.size() >= MAX_CLASSES) {
                log.info("   ✋ Reached maximum of {} classes, stopping", MAX_CLASSES);
                break;
            }

            if (className != null) {
                log.debug("   Extracted class '{}' from chunk '{}'", className, match.id());

                // Find by simple class name in Neo4j
                String simpleClassName = className.substring(className.lastIndexOf('.') + 1);

                List<GraphNode> nodes = graphRepo.findByRepoName(repoName).stream()
                        .filter(n -> simpleClassName.equals(n.getSimpleName()))
                        .toList();

                if (!nodes.isEmpty()) {
                    candidates.addAll(nodes);
                    processedClasses.add(className);  // Mark as processed
                    pineconeMatchesFound += nodes.size();

                    // CRITICAL: Track method-level information
                    if (methodName != null && !methodName.isEmpty()) {
                        methodMatches.computeIfAbsent(className, k -> new ArrayList<>())
                                .add(new MethodMatch(methodName, match.score(), match.content(), chunkType));
                        log.info("   ✅ Added class '{}' with target method '{}' (score: {:.2f})",
                                simpleClassName, methodName, match.score());
                    } else {
                        log.info("   ✅ Added class '{}' (score: {:.2f})", simpleClassName, match.score());
                    }
                } else {
                    log.debug("   ⚠️ No Neo4j nodes found for class '{}'", simpleClassName);
                }
            } else {
                log.debug("   ⚠️ Could not extract class name from chunk ID: '{}'", match.id());
            }
        }

        log.info("   ✅ Matched {} high-relevance chunks to {} unique classes",
                filteredMatches.size(), processedClasses.size());

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
                .map(node -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("className", node.getSimpleName());
                    data.put("filePath", node.getFilePath() != null ? node.getFilePath() : "");
                    data.put("purpose", node.getSummary() != null ? node.getSummary() : "");
                    data.put("dependencies", node.getDomain() != null ? node.getDomain() : "");

                    // CRITICAL: Include method-level matches if available
                    String fqn = node.getFullyQualifiedName();
                    if (methodMatches.containsKey(fqn)) {
                        List<MethodMatch> methods = methodMatches.get(fqn);
                        List<Map<String, Object>> methodData = methods.stream()
                                .map(m -> {
                                    Map<String, Object> md = new HashMap<>();
                                    md.put("name", m.methodName());
                                    md.put("score", String.format("%.2f", m.score()));
                                    md.put("type", m.chunkType());
                                    // Include a snippet of the method content
                                    String snippet = m.content().length() > 200
                                            ? m.content().substring(0, 200) + "..."
                                            : m.content();
                                    md.put("snippet", snippet);
                                    return md;
                                })
                                .toList();
                        data.put("targetMethods", methodData);
                        log.info("   🎯 Including {} target methods for class '{}'",
                                methodData.size(), node.getSimpleName());
                    }

                    return data;
                })
                .toList();

        String prompt = promptLibrary.render("scope-discovery", Map.of(
                "requirement", req.getSummary(),
                "domain", req.getDomain() != null ? req.getDomain() : "unknown",
                "candidates", candidateData
        ));

        log.info("\n📤 SENDING TO LLM:");
        log.info("   Requirement: {}", req.getSummary());
        log.info("   Candidates: {} classes", candidateData.size());
        log.info("   With method-level targeting: {}",
                methodMatches.isEmpty() ? "No" : "Yes (" + methodMatches.size() + " classes)");

        try {
            // Use instrumented call with agent name for proper logging
            String jsonResponse = geminiClient.callChatApi(prompt, "ScopeDiscoveryAgent", null);

            // Strip markdown code blocks if present (LLM sometimes wraps JSON in ```json ... ```)
            String cleanedJson = stripMarkdownCodeBlocks(jsonResponse);

            ScopeProposalDTO dto = objectMapper.readValue(cleanedJson, ScopeProposalDTO.class);
            return convertToScopeProposal(dto, candidates);
        } catch (Exception e) {
            log.error("LLM scope analysis failed, using fallback", e);
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
                // CRITICAL: Populate target methods from Pinecone matches
                List<String> targetMethods = null;
                String fqn = node.getFullyQualifiedName();
                if (methodMatches.containsKey(fqn)) {
                    targetMethods = methodMatches.get(fqn).stream()
                            .map(MethodMatch::methodName)
                            .toList();
                    log.info("   🎯 FileAction for '{}' targets methods: {}",
                            node.getSimpleName(), targetMethods);
                }

                proposal.getFilesToModify().add(FileAction.builder()
                        .filePath(path)
                        .actionType(FileAction.ActionType.MODIFY)
                        .className(node.getFullyQualifiedName())
                        .targetMethods(targetMethods)
                        .reason("Required for task")
                        .build());
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
        ScopeProposal proposal = ScopeProposal.builder()
                .reasoning("Fallback - LLM analysis failed")
                .estimatedComplexity(5)
                .build();

        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            GraphNode node = candidates.get(i);

            // Include method-level targeting even in fallback
            List<String> targetMethods = null;
            String fqn = node.getFullyQualifiedName();
            if (methodMatches.containsKey(fqn)) {
                targetMethods = methodMatches.get(fqn).stream()
                        .map(MethodMatch::methodName)
                        .toList();
            }

            proposal.getFilesToModify().add(FileAction.builder()
                    .filePath(node.getFilePath())
                    .actionType(FileAction.ActionType.MODIFY)
                    .className(node.getFullyQualifiedName())
                    .targetMethods(targetMethods)
                    .reason("Required")
                    .build());
        }
        return proposal;
    }

    /**
     * Extract class name from Pinecone chunk ID.
     * Format: "repo:com.package.ClassName.methodName" → "com.package.ClassName"
     * Format: "repo:com.package.ClassName" → "com.package.ClassName"
     */
    private String extractClassNameFromChunkId(String chunkId) {
        try {
            // Remove repo prefix: "repo:com.package.ClassName.methodName"
            if (!chunkId.contains(":")) {
                return null;
            }

            String afterRepo = chunkId.substring(chunkId.indexOf(':') + 1);

            // Handle different formats:
            // - "com.package.ClassName" (class chunk)
            // - "com.package.ClassName.methodName" (method chunk)
            // - "com.package.ClassName.fieldName_field" (field chunk)

            // Split by dots and remove last part if it's a method/field name
            String[] parts = afterRepo.split("\\.");

            if (parts.length < 2) {
                return null; // Invalid format
            }

            // Check if last part looks like a method/field (lowercase or ends with _field)
            String lastPart = parts[parts.length - 1];
            boolean isMethodOrField = Character.isLowerCase(lastPart.charAt(0)) ||
                    lastPart.endsWith("_field");

            if (isMethodOrField) {
                // Remove last part to get class name
                return String.join(".", java.util.Arrays.copyOf(parts, parts.length - 1));
            } else {
                // It's already a class name
                return afterRepo;
            }

        } catch (Exception e) {
            log.debug("Failed to extract class name from chunk ID: {}", chunkId, e);
            return null;
        }
    }

    /**
     * Strip markdown code blocks from LLM response.
     * LLMs sometimes wrap JSON in ```json ... ``` which breaks JSON parsing.
     */
    private String stripMarkdownCodeBlocks(String response) {
        if (response == null) {
            return response;
        }

        String trimmed = response.trim();

        // Remove ```json ... ``` or ``` ... ``` wrapping
        if (trimmed.startsWith("```")) {
            // Find the end of the opening fence (could be ```json or just ```)
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }

            // Remove closing fence
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }

            return trimmed.trim();
        }

        return response;
    }

    /**
     * Calculate adaptive relevance threshold based on score distribution.
     *
     * Strategy:
     * 1. Find score gaps (significant drops between consecutive matches)
     * 2. Use the largest gap as natural cutoff point
     * 3. Ensure we take at least top 3 matches (if available)
     * 4. Enforce minimum threshold of 0.5 (50% similarity)
     * 5. Enforce maximum threshold of 0.8 (avoid being too strict)
     *
     * Examples:
     * - Scores: [0.95, 0.92, 0.88, 0.45, 0.40] → threshold ~0.88 (gap of 0.43)
     * - Scores: [0.75, 0.73, 0.71, 0.70] → threshold ~0.70 (no clear gap, take top N)
     * - Scores: [0.45, 0.44, 0.42] → threshold 0.5 (all below min, use minimum)
     */
    private double calculateAdaptiveThreshold(List<PineconeRetriever.CodeContext> matches) {
        if (matches == null || matches.isEmpty()) {
            return 0.5; // Default minimum
        }

        final double MIN_THRESHOLD = 0.5;  // Never go below 50% similarity
        final double MAX_THRESHOLD = 0.8;  // Never be stricter than 80%
        final int MIN_MATCHES = 3;          // Always consider at least top 3
        final double SIGNIFICANT_GAP = 0.15; // Gap of 15% is significant

        // If we have very few matches, just use minimum threshold
        if (matches.size() <= MIN_MATCHES) {
            double topScore = matches.get(0).score();
            return Math.max(MIN_THRESHOLD, topScore * 0.7); // 70% of top score
        }

        // Find the largest score gap in top 10 matches
        double largestGap = 0;
        int gapIndex = -1;

        for (int i = 0; i < Math.min(matches.size() - 1, 10); i++) {
            double gap = matches.get(i).score() - matches.get(i + 1).score();
            if (gap > largestGap && i >= (MIN_MATCHES - 1)) {
                // Only consider gaps after we've included minimum matches
                largestGap = gap;
                gapIndex = i;
            }
        }

        double threshold;

        if (largestGap >= SIGNIFICANT_GAP && gapIndex >= 0) {
            // Found a significant gap - use score after the gap as threshold
            threshold = matches.get(gapIndex).score();
            log.debug("   📊 Found significant gap ({:.3f}) at index {}", largestGap, gapIndex);
        } else {
            // No significant gap - use relative threshold (70% of top score)
            double topScore = matches.get(0).score();
            threshold = topScore * 0.7;
            log.debug("   📊 No significant gap found, using 70% of top score ({:.3f})", topScore);
        }

        // Enforce bounds
        threshold = Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, threshold));

        log.debug("   📊 Final adaptive threshold: {:.3f}", threshold);
        return threshold;
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
