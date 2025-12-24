package com.purchasingpower.autoflow.workflow.agents;

import com.purchasingpower.autoflow.parser.EntityExtractor;
import com.purchasingpower.autoflow.service.GitOperationsService;
import com.purchasingpower.autoflow.service.MavenBuildService;
import com.purchasingpower.autoflow.service.PineconeIngestService;
import com.purchasingpower.autoflow.storage.Neo4jGraphStore;
import com.purchasingpower.autoflow.model.neo4j.ParsedCodeGraph;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CodeIndexerAgent {

    private final GitOperationsService gitService;
    private final MavenBuildService buildService;  // FIX: Use existing MavenBuildService, not MavenRunnerService
    private final PineconeIngestService pineconeIngest;
    private final EntityExtractor entityExtractor;
    private final Neo4jGraphStore neo4jStore;

    public Map<String, Object> execute(WorkflowState state) {
        log.info("📦 Indexing codebase: {}", state.getRepoUrl());

        try {
            // Clone repository
            File workspace = gitService.cloneRepository(state.getRepoUrl(), state.getBaseBranch());

            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("workspaceDir", workspace);

            log.info("✅ Cloned to: {}", workspace.getAbsolutePath());

            // Baseline build
            log.info("🔨 Running baseline build...");
            BuildResult baseline = buildService.buildAndVerify(workspace);
            updates.put("baselineBuild", baseline);

            if (!baseline.isSuccess()) {
                updates.put("lastAgentDecision", AgentDecision.askDev(
                        "❌ **Repository Has Compilation Errors**\n\n" +
                                "Cannot work on broken codebase.\n\n" +
                                "**Errors:**\n```\n" + baseline.getErrors() + "\n```\n\n" +
                                "Options:\n1. Fix errors first\n2. Abort"
                ));
                return updates;
            }

            // Ingest to Pinecone - FIX: Returns boolean, not IndexingResult
            String repoName = extractRepoName(state.getRepoUrl());
            boolean hasCode = pineconeIngest.ingestRepository(workspace, repoName);

            if (!hasCode) {
                log.info("📝 New/empty project detected");
            } else {
                log.info("✅ Code indexed to Pinecone");
            }

            // Parse and index to Neo4j
            try {
                // Find all Java files
                List<Path> javaFiles = findJavaFiles(workspace.toPath());

                int totalClasses = 0;
                int totalMethods = 0;

                for (Path javaFile : javaFiles) {
                    try {
                        ParsedCodeGraph graph = entityExtractor.extractFromFile(javaFile);
                        neo4jStore.storeCodeGraph(graph);
                        totalClasses += graph.getClasses().size();
                        totalMethods += graph.getMethods().size();
                    } catch (Exception e) {
                        log.warn("Failed to parse {}: {}", javaFile, e.getMessage());
                    }
                }

                log.info("✅ Code graph stored in Neo4j: {} classes, {} methods from {} files",
                        totalClasses, totalMethods, javaFiles.size());
            } catch (Exception e) {
                log.warn("Failed to index to Neo4j: {}", e.getMessage());
            }

            updates.put("lastAgentDecision", AgentDecision.proceed("Code indexing complete"));
            return updates;

        } catch (Exception e) {
            log.error("❌ Indexing failed", e);
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("lastAgentDecision", AgentDecision.error("Indexing failed: " + e.getMessage()));
            return updates;
        }
    }

    private String extractRepoName(String repoUrl) {
        String[] parts = repoUrl.replace(".git", "").split("/");
        return parts[parts.length - 1];
    }

    private List<Path> findJavaFiles(Path rootDir) throws Exception {
        try (var walk = Files.walk(rootDir)) {
            return walk
                    .filter(p -> !Files.isDirectory(p))
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/test/"))  // Skip test files
                    .collect(Collectors.toList());
        }
    }
}