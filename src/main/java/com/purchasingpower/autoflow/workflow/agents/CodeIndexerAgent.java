package com.purchasingpower.autoflow.workflow.agents;

import com.purchasingpower.autoflow.model.neo4j.ParsedCodeGraph;
import com.purchasingpower.autoflow.model.sync.EmbeddingSyncResult;
import com.purchasingpower.autoflow.parser.EntityExtractor;
import com.purchasingpower.autoflow.service.GitOperationsService;
import com.purchasingpower.autoflow.service.IncrementalEmbeddingSyncService;
import com.purchasingpower.autoflow.service.MavenBuildService;
import com.purchasingpower.autoflow.storage.Neo4jGraphStore;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CodeIndexerAgent - Indexes repository code for RAG retrieval.
 *
 * Uses IncrementalEmbeddingSyncService for smart Pinecone updates.
 * Uses EntityExtractor + Neo4jGraphStore for knowledge graph.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeIndexerAgent {

    private final GitOperationsService gitService;
    private final MavenBuildService buildService;
    private final IncrementalEmbeddingSyncService embeddingSyncService;
    private final EntityExtractor entityExtractor;
    private final Neo4jGraphStore neo4jGraphStore;

    public Map<String, Object> execute(WorkflowState state) {
        log.info("📦 Indexing codebase: {}", state.getRepoUrl());

        try {
            Map<String, Object> updates = new HashMap<>(state.toMap());

            // Extract repo name
            String repoName = extractRepoName(state.getRepoUrl());
            log.info("Repository name: {}", repoName);

            // ================================================================
            // STEP 1: CLONE OR REUSE WORKSPACE
            // ================================================================

            File workspace = getOrCloneWorkspace(state.getRepoUrl(), state.getBaseBranch(), repoName);
            updates.put("workspaceDir", workspace.getAbsolutePath());  // CRITICAL: Store as String!

            log.info("✅ Workspace ready: {}", workspace.getAbsolutePath());

            // ================================================================
            // STEP 2: BASELINE BUILD
            // ================================================================

            log.info("🔨 Running baseline build...");
            BuildResult baseline = buildService.buildAndVerify(workspace);
            updates.put("baselineBuild", baseline);

            if (!baseline.isSuccess()) {
                updates.put("lastAgentDecision", AgentDecision.askDev(
                        "❌ **Repository Has Compilation Errors**\n\n" +
                                "Cannot work on broken codebase.\n\n" +
                                "**Errors:**\n```\n" + baseline.getErrors() + "\n```\n\n" +
                                "Options:\n1. Fix errors first\n2. Force index anyway (risky)"
                ));
                return updates;
            }

            // ================================================================
            // STEP 3: PINECONE INCREMENTAL SYNC
            // ================================================================

            log.info("🔄 Syncing embeddings to Pinecone...");
            EmbeddingSyncResult syncResult = embeddingSyncService.syncEmbeddings(workspace, repoName);

            log.info("✅ Pinecone sync complete:");
            log.info("   Type: {}", syncResult.getSyncType());
            log.info("   Files analyzed: {}", syncResult.getFilesAnalyzed());
            log.info("   Files changed: {}", syncResult.getFilesChanged());
            log.info("   Chunks created: {}", syncResult.getChunksCreated());
            log.info("   Chunks deleted: {}", syncResult.getChunksDeleted());
            log.info("   Duration: {}ms", syncResult.getTotalTimeMs());

            // ================================================================
            // STEP 4: NEO4J GRAPH UPDATE
            // ================================================================

            log.info("🔄 Updating Neo4j code graph...");

            // Find all Java files in workspace
            List<Path> javaFiles = findAllJavaFiles(workspace);
            log.info("Found {} Java files to process", javaFiles.size());

            // Parse each file and combine into one graph
            List<String> parseErrors = new ArrayList<>();
            ParsedCodeGraph combinedGraph = ParsedCodeGraph.builder().build();

            for (Path javaFile : javaFiles) {
                try {
                    ParsedCodeGraph fileGraph = entityExtractor.extractFromFile(javaFile);

                    // Merge into combined graph
                    combinedGraph.getClasses().addAll(fileGraph.getClasses());
                    combinedGraph.getMethods().addAll(fileGraph.getMethods());
                    combinedGraph.getFields().addAll(fileGraph.getFields());
                    combinedGraph.getRelationships().addAll(fileGraph.getRelationships());

                } catch (Exception e) {
                    String errorMsg = javaFile.getFileName() + ": " + e.getMessage();
                    parseErrors.add(errorMsg);
                    log.warn("Failed to parse {}: {}", javaFile.getFileName(), e.getMessage());
                }
            }

            // Store entire graph (MERGE handles updates automatically!)
            neo4jGraphStore.storeCodeGraph(combinedGraph);

            log.info("✅ Neo4j graph updated:");
            log.info("   Classes: {}", combinedGraph.getClasses().size());
            log.info("   Methods: {}", combinedGraph.getMethods().size());
            log.info("   Fields: {}", combinedGraph.getFields().size());
            log.info("   Relationships: {}", combinedGraph.getRelationships().size());

            // ================================================================
            // STEP 5: BUILD INDEXING RESULT (USING ACTUAL FIELDS!)
            // ================================================================

            // Determine IndexType based on sync result
            IndexingResult.IndexType indexType;
            switch (syncResult.getSyncType()) {
                case INITIAL_FULL_INDEX:
                case FORCED_FULL_REINDEX:
                    indexType = IndexingResult.IndexType.FULL;
                    break;
                case INCREMENTAL:
                    indexType = IndexingResult.IndexType.INCREMENTAL;
                    break;
                case NO_CHANGES:
                    indexType = IndexingResult.IndexType.SKIPPED;
                    break;
                default:
                    indexType = IndexingResult.IndexType.FULL;
            }

            IndexingResult indexingResult = IndexingResult.builder()
                    .success(true)
                    .filesProcessed(javaFiles.size())                      // ← Correct field name!
                    .chunksCreated(syncResult.getChunksCreated())
                    .graphNodesCreated(
                            combinedGraph.getClasses().size() +
                                    combinedGraph.getMethods().size() +
                                    combinedGraph.getFields().size()
                    )
                    .graphEdgesCreated(combinedGraph.getRelationships().size())
                    .indexedCommit(syncResult.getToCommit())               // ← Git commit hash
                    .indexType(indexType)                                   // ← Enum, not String!
                    .errors(parseErrors)
                    .durationMs(syncResult.getTotalTimeMs())
                    .build();

            updates.put("indexingResult", indexingResult);
            updates.put("lastAgentDecision", AgentDecision.proceed(
                    String.format("Indexed %d classes, %d methods (%s)",
                            combinedGraph.getClasses().size(),
                            combinedGraph.getMethods().size(),
                            indexType)
            ));

            return updates;

        } catch (Exception e) {
            log.error("Code indexing failed", e);
            Map<String, Object> updates = new HashMap<>(state.toMap());

            // Return error result
            IndexingResult errorResult = IndexingResult.builder()
                    .success(false)
                    .errors(List.of(e.getMessage()))
                    .indexType(IndexingResult.IndexType.FULL)
                    .build();

            updates.put("indexingResult", errorResult);
            updates.put("lastAgentDecision", AgentDecision.error("Failed to index code: " + e.getMessage()));
            return updates;
        }
    }

    /**
     * Find all Java source files in workspace
     */
    private List<Path> findAllJavaFiles(File workspace) throws IOException {
        Path srcMainJava = workspace.toPath().resolve("src/main/java");

        if (!Files.exists(srcMainJava)) {
            log.warn("No src/main/java directory found in workspace");
            return new ArrayList<>();
        }

        try (Stream<Path> paths = Files.walk(srcMainJava)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Clone repository or reuse existing workspace
     */
    private File getOrCloneWorkspace(String repoUrl, String branch, String repoName) {
        // Define workspace location
        String workspaceBase = System.getProperty("user.home") + "/ai-workspace";
        File workspace = new File(workspaceBase, repoName);

        // Check if workspace exists and is valid Git repo
        if (workspace.exists() && workspace.isDirectory()) {
            File gitDir = new File(workspace, ".git");
            if (gitDir.exists() && gitDir.isDirectory()) {
                log.info("✅ Reusing existing workspace: {}", workspace.getAbsolutePath());

                // Pull latest changes (if Git service supports it)
                try {
                    log.info("Pulling latest changes...");
                    // TODO: Add gitService.pullLatestChanges(workspace) when method exists
                    return workspace;
                } catch (Exception e) {
                    log.warn("Failed to pull changes: {}", e.getMessage());
                    // Fall through to re-clone
                }
            }
        }

        // Clone fresh
        log.info("🔄 Cloning repository...");
        return gitService.cloneRepository(repoUrl, branch);
    }

    /**
     * Extract repository name from URL
     * Example: "https://github.com/user/repo.git" → "repo"
     */
    private String extractRepoName(String repoUrl) {
        String name = repoUrl;

        // Remove .git suffix
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }

        // Get last part after /
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }

        return name;
    }
}