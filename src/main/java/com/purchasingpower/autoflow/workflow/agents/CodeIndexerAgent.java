package com.purchasingpower.autoflow.workflow.agents;

import com.purchasingpower.autoflow.service.GitOperationsService;
import com.purchasingpower.autoflow.service.MavenBuildService;
import com.purchasingpower.autoflow.service.impl.IncrementalEmbeddingSyncServiceImpl;
import com.purchasingpower.autoflow.service.graph.GraphPersistenceService;
import com.purchasingpower.autoflow.service.AstParserService;
import com.purchasingpower.autoflow.workflow.state.*;
import com.purchasingpower.autoflow.model.sync.EmbeddingSyncResult;
import com.purchasingpower.autoflow.model.ast.CodeChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/**
 * AGENT 2: Code Indexer
 *
 * Purpose: Clone repo, validate it compiles, index in Pinecone + Oracle
 *
 * CRITICAL: Does NOT work on broken repos
 *
 * Steps:
 * 1. Clone repo
 * 2. Baseline build (must pass!)
 * 3. Incremental sync to Pinecone
 * 4. Index in Oracle graph
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeIndexerAgent {

    private final GitOperationsService gitService;
    private final MavenBuildService buildService;
    private final IncrementalEmbeddingSyncServiceImpl embeddingSync;
    private final AstParserService astParser;
    private final GraphPersistenceService graphPersistence;

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

            // Step 4: Parse for Oracle graph
            log.info("📊 Building knowledge graph...");
            List<CodeChunk> chunks = parseAllJavaFiles(workspace);
            state.setParsedCode(chunks);

            // Step 5: Persist to Oracle
            graphPersistence.persistChunks(
                    chunks,
                    extractRepoName(state.getRepoUrl())
            );

            log.info("✅ Graph indexed: {} nodes", chunks.size());

            // Step 6: Store indexing result
            state.setIndexingResult(IndexingResult.builder()
                    .success(true)
                    .filesProcessed(syncResult.getFilesAnalyzed())
                    .chunksCreated(syncResult.getChunksCreated())
                    .build());

            return AgentDecision.proceed("Indexing complete, proceeding to context building");

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

    private List<CodeChunk> parseAllJavaFiles(File workspace) {
        // Find all Java files
        List<File> javaFiles = findJavaFiles(workspace);

        // Parse with AST
        return astParser.parseJavaFiles(
                javaFiles,
                extractRepoName(workspace.getName())
        );
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