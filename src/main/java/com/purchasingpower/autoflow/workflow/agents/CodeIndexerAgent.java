package com.purchasingpower.autoflow.workflow.agents;

import com.purchasingpower.autoflow.parser.EntityExtractor;
import com.purchasingpower.autoflow.service.GitOperationsService;
import com.purchasingpower.autoflow.service.MavenBuildService;
import com.purchasingpower.autoflow.service.impl.IncrementalEmbeddingSyncServiceImpl;
import com.purchasingpower.autoflow.storage.Neo4jGraphStore;
import com.purchasingpower.autoflow.model.neo4j.ParsedCodeGraph;
import com.purchasingpower.autoflow.workflow.state.*;
import com.purchasingpower.autoflow.model.sync.EmbeddingSyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/**
 * AGENT 2: Code Indexer
 *
 * Purpose: Clone repo, validate it compiles, index in Pinecone + Neo4j
 *
 * HYBRID STORAGE (Oracle removed):
 * - Pinecone: Semantic vector search
 * - Neo4j: Code structure graph (SOLVES chunking problem!)
 *
 * CRITICAL: Does NOT work on broken repos
 *
 * Steps:
 * 1. Clone repo
 * 2. Baseline build (must pass!)
 * 3. Incremental sync to Pinecone (semantic search)
 * 4. Extract code entities + relationships (JavaParser)
 * 5. Index in Neo4j graph (structure preservation)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeIndexerAgent {

    private final GitOperationsService gitService;
    private final MavenBuildService buildService;
    private final IncrementalEmbeddingSyncServiceImpl embeddingSync;

    // Neo4j components for hybrid retrieval
    private final EntityExtractor entityExtractor;
    private final Neo4jGraphStore neo4jStore;

    public AgentDecision execute(WorkflowState state) {
        log.info("📦 Indexing codebase: {}", state.getRepoUrl());

        try {
            // Step 1: Clone repository
            File workspace = gitService.cloneRepository(
                    state.getRepoUrl(),
                    state.getBaseBranch()
            );
            state.setWorkspaceDir(workspace);
            log.info("✅ Cloned to: {}", workspace.getAbsolutePath());

            // Step 2: CRITICAL - Baseline build
            log.info("🔨 Running baseline build (MUST pass)...");
            BuildResult baseline = buildService.buildAndVerify(workspace);
            state.setBaselineBuild(baseline);

            if (!baseline.isSuccess()) {
                // STOP - Don't work on broken code
                return AgentDecision.askDev(
                        "❌ **Repository Has Compilation Errors**\n\n" +
                                "I cannot work on a broken codebase.\n\n" +
                                "**Errors:**\n```\n" +
                                baseline.getErrors() +
                                "\n```\n\n" +
                                "**Options:**\n" +
                                "1. Fix these errors first, then try again\n" +
                                "2. Tell me to fix THESE errors specifically\n" +
                                "3. Work on a different branch that compiles"
                );
            }

            log.info("✅ Baseline build passed");

            // Step 3: Sync to Pinecone (incremental)
            log.info("🔄 Syncing embeddings to Pinecone...");
            EmbeddingSyncResult syncResult = embeddingSync.syncEmbeddings(
                    workspace,
                    extractRepoName(state.getRepoUrl())
            );

            log.info("✅ Pinecone sync: {} chunks", syncResult.getChunksCreated());

            // Step 4: NEW - Extract code entities for Neo4j knowledge graph
            log.info("📊 Extracting code entities and relationships for Neo4j...");
            List<File> javaFiles = findJavaFiles(workspace);
            int totalEntities = 0;
            int totalRelationships = 0;

            for (File javaFile : javaFiles) {
                try {
                    ParsedCodeGraph graph = entityExtractor.extractFromFile(javaFile.toPath());

                    if (!graph.hasErrors()) {
                        neo4jStore.storeCodeGraph(graph);
                        totalEntities += graph.getTotalEntities();
                        totalRelationships += graph.getTotalRelationships();
                    } else {
                        log.warn("Parsing errors in {}: {}", javaFile.getName(), graph.getErrors());
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract entities from {}: {}", javaFile.getName(), e.getMessage());
                }
            }

            log.info("✅ Neo4j graph indexed: {} entities, {} relationships",
                    totalEntities, totalRelationships);

            // Step 5: Store indexing result
            state.setIndexingResult(IndexingResult.builder()
                    .success(true)
                    .filesProcessed(syncResult.getFilesAnalyzed())
                    .chunksCreated(syncResult.getChunksCreated())
                    .build());

            return AgentDecision.proceed(String.format(
                    "Indexing complete: %d Pinecone chunks, %d Neo4j entities, %d relationships. " +
                    "Hybrid retrieval enabled - NO MORE CHUNKING PROBLEMS!",
                    syncResult.getChunksCreated(), totalEntities, totalRelationships));

        } catch (Exception e) {
            log.error("❌ Indexing failed", e);
            return AgentDecision.error("Indexing failed: " + e.getMessage());
        }
    }

    private String extractRepoName(String repoUrl) {
        // Extract repo name from URL
        // https://github.com/user/repo.git → repo
        String[] parts = repoUrl.replace(".git", "").split("/");
        return parts[parts.length - 1];
    }

    private List<File> findJavaFiles(File root) {
        try (java.util.stream.Stream<java.nio.file.Path> walk =
                     java.nio.file.Files.walk(root.toPath())) {
            return walk
                    .filter(p -> !java.nio.file.Files.isDirectory(p))
                    .map(java.nio.file.Path::toFile)
                    .filter(f -> f.getName().endsWith(".java"))
                    .filter(f -> f.getAbsolutePath().contains("src/main/java"))
                    .toList();
        } catch (Exception e) {
            log.error("Failed to find Java files", e);
            return List.of();
        }
    }
}