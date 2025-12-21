package com.purchasingpower.autoflow.workflow.agents;

import com.purchasingpower.autoflow.model.graph.GraphNode;
import com.purchasingpower.autoflow.model.neo4j.ClassNode;
import com.purchasingpower.autoflow.model.neo4j.MethodNode;
import com.purchasingpower.autoflow.model.neo4j.FieldNode;
import com.purchasingpower.autoflow.query.HybridRetriever;
import com.purchasingpower.autoflow.repository.GraphNodeRepository;
import com.purchasingpower.autoflow.service.graph.GraphTraversalService;
import com.purchasingpower.autoflow.storage.Neo4jGraphStore;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AGENT 4: Context Builder
 *
 * Purpose: Get EXACT code for files in scope
 *
 * UPDATED: Now uses HYBRID RETRIEVAL (Pinecone + Neo4j)
 * - Current file content
 * - Dependencies (what it uses) - FROM NEO4J GRAPH!
 * - Dependents (what uses it) - FROM NEO4J GRAPH!
 * - Method callers (what calls its methods) - FROM NEO4J GRAPH!
 * - Domain context (business rules)
 *
 * SOLVES CHUNKING PROBLEM:
 * - Neo4j preserves ALL code relationships
 * - Can answer: "What calls this?", "What depends on this?", "What will break?"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextBuilderAgent {

    private final GraphNodeRepository graphRepo;
    private final GraphTraversalService graphTraversal;

    // NEW: Neo4j hybrid retrieval components
    private final Neo4jGraphStore neo4jStore;
    private final HybridRetriever hybridRetriever;

    public AgentDecision execute(WorkflowState state) {
        log.info("🔨 Building exact context for {} files...",
                state.getScopeProposal().getTotalFileCount());

        try {
            ScopeProposal scope = state.getScopeProposal();
            String repoName = extractRepoName(state.getRepoUrl());
            File workspace = state.getWorkspaceDir();

            StructuredContext context = buildContext(scope, repoName, workspace);
            state.setContext(context);

            log.info("✅ Context built. Confidence: {}, Files: {}",
                    context.getConfidence(),
                    context.getFileContexts().size());

            if (context.getConfidence() < 0.9) {
                return AgentDecision.askDev(
                        "⚠️ **Uncertain Context**\n\n" +
                                "I couldn't get complete context for some files.\n" +
                                "This may result in incorrect code generation.\n\n" +
                                "Proceed anyway?"
                );
            }

            return AgentDecision.proceed("Context ready for code generation");

        } catch (Exception e) {
            log.error("Failed to build context", e);
            return AgentDecision.error("Context building failed: " + e.getMessage());
        }
    }

    /**
     * Build structured context using KNOWLEDGE GRAPH
     */
    private StructuredContext buildContext(
            ScopeProposal scope,
            String repoName,
            File workspace) throws Exception {

        Map<String, StructuredContext.FileContext> fileContexts = new HashMap<>();
        int successCount = 0;
        int totalFiles = scope.getTotalFileCount();

        // Process files to modify
        for (FileAction action : scope.getFilesToModify()) {
            StructuredContext.FileContext fileCtx = buildFileContext(
                    action, repoName, workspace, true
            );

            if (fileCtx != null) {
                fileContexts.put(action.getFilePath(), fileCtx);
                successCount++;
            }
        }

        // Process files to create (no current code)
        for (FileAction action : scope.getFilesToCreate()) {
            StructuredContext.FileContext fileCtx = buildFileContext(
                    action, repoName, workspace, false
            );

            if (fileCtx != null) {
                fileContexts.put(action.getFilePath(), fileCtx);
                successCount++;
            }
        }

        // Build domain context using KNOWLEDGE GRAPH
        StructuredContext.DomainContext domainCtx = buildDomainContext(
                scope, repoName
        );

        double confidence = (double) successCount / totalFiles;

        return StructuredContext.builder()
                .fileContexts(fileContexts)
                .domainContext(domainCtx)
                .confidence(confidence)
                .build();
    }

    /**
     * Build context for single file using KNOWLEDGE GRAPH
     */
    private StructuredContext.FileContext buildFileContext(
            FileAction action,
            String repoName,
            File workspace,
            boolean fileExists) throws Exception {

        String nodeId = repoName + ":" + action.getClassName();

        // Get node from knowledge graph
        GraphNode node = graphRepo.findById(nodeId).orElse(null);

        if (node == null && fileExists) {
            log.warn("Node not found in graph: {}", nodeId);
            // Try by file path
            node = graphRepo.findByRepoName(repoName).stream()
                    .filter(n -> action.getFilePath().equals(n.getFilePath()))
                    .findFirst()
                    .orElse(null);
        }

        // Read current code
        String currentCode = "";
        if (fileExists) {
            File file = new File(workspace, action.getFilePath());
            if (file.exists()) {
                currentCode = Files.readString(file.toPath());
            }
        }

        // Get dependencies using HYBRID approach:
        // 1. Legacy Oracle graph (for backward compatibility)
        // 2. NEW: Neo4j graph (complete relationship tracking)
        List<String> dependencies = new ArrayList<>();
        List<String> dependents = new ArrayList<>();

        if (node != null) {
            // Legacy Oracle dependencies
            dependencies = graphTraversal.findDirectDependencies(nodeId, repoName);
            dependents = graphTraversal.findDirectDependents(nodeId, repoName);
        }

        // NEW: Get dependencies from Neo4j (SOLVES chunking problem!)
        String className = action.getClassName();
        try {
            ClassNode neo4jClass = neo4jStore.findClassById("CLASS:" + className);
            if (neo4jClass != null) {
                // Get Neo4j dependencies (extends, implements, uses)
                List<ClassNode> neo4jDeps = neo4jStore.findClassDependencies(neo4jClass.getFullyQualifiedName());
                List<String> neo4jDepNames = neo4jDeps.stream()
                        .map(ClassNode::getFullyQualifiedName)
                        .collect(Collectors.toList());

                // Merge with Oracle dependencies (remove duplicates)
                neo4jDepNames.forEach(dep -> {
                    if (!dependencies.contains(dep)) {
                        dependencies.add(dep);
                    }
                });

                log.info("Neo4j found {} additional dependencies for {}",
                        neo4jDepNames.size(), className);
            }
        } catch (Exception e) {
            log.warn("Failed to get Neo4j dependencies for {}: {}", className, e.getMessage());
        }

        return StructuredContext.FileContext.builder()
                .filePath(action.getFilePath())
                .currentCode(currentCode)
                .purpose(node != null ? node.getSummary() : action.getReason())
                .dependencies(dependencies)
                .dependents(dependents)
                .coveredByTests(new ArrayList<>()) // TODO: Find test coverage
                .build();
    }

    /**
     * Build domain context using KNOWLEDGE GRAPH
     */
    private StructuredContext.DomainContext buildDomainContext(
            ScopeProposal scope,
            String repoName) {

        // Get domain from first file
        String domain = scope.getFilesToModify().isEmpty() ? "unknown" :
                extractDomain(scope.getFilesToModify().get(0).getClassName());

        // Query knowledge graph for all classes in this domain
        List<GraphNode> domainNodes = graphRepo.findByRepoName(repoName).stream()
                .filter(node -> domain.equalsIgnoreCase(node.getDomain()))
                .toList();

        List<String> domainClasses = domainNodes.stream()
                .map(GraphNode::getFullyQualifiedName)
                .collect(Collectors.toList());

        // Extract business rules from knowledge graph metadata
        List<String> businessRules = domainNodes.stream()
                .map(GraphNode::getBusinessCapability)
                .distinct()
                .filter(cap -> cap != null && !cap.isEmpty())
                .collect(Collectors.toList());

        // Extract concepts
        List<String> concepts = domainNodes.stream()
                .flatMap(node -> node.getConcepts() != null ?
                        node.getConcepts().stream() : new ArrayList<String>().stream())
                .distinct()
                .collect(Collectors.toList());

        return StructuredContext.DomainContext.builder()
                .domain(domain)
                .businessRules(businessRules)
                .concepts(concepts)
                .architecturePattern("Spring Boot MVC") // TODO: Detect from graph
                .domainClasses(domainClasses)
                .build();
    }

    private String extractDomain(String className) {
        // Extract domain from package name
        // e.g. com.example.payment.PaymentService → payment
        String[] parts = className.split("\\.");
        if (parts.length > 3) {
            return parts[parts.length - 2]; // Second to last is usually domain
        }
        return "unknown";
    }

    private String extractRepoName(String repoUrl) {
        String[] parts = repoUrl.replace(".git", "").split("/");
        return parts[parts.length - 1];
    }
}