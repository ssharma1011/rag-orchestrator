package com.purchasingpower.autoflow.workflow.agents;

import com.purchasingpower.autoflow.model.neo4j.ParsedCodeGraph;
import com.purchasingpower.autoflow.model.sync.EmbeddingSyncResult;
import com.purchasingpower.autoflow.parser.EntityExtractor;
import com.purchasingpower.autoflow.service.GitOperationsService;
import com.purchasingpower.autoflow.service.IncrementalEmbeddingSyncService;
import com.purchasingpower.autoflow.service.MavenBuildService;
import com.purchasingpower.autoflow.storage.Neo4jGraphStore;
import com.purchasingpower.autoflow.util.GitUrlParser;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    private final com.purchasingpower.autoflow.repository.GraphNodeRepository graphNodeRepository;

    @Transactional
    public Map<String, Object> execute(WorkflowState state) {
        log.info("📦 Indexing codebase: {}", state.getRepoUrl());

        try {
            Map<String, Object> updates = new HashMap<>(state.toMap());

            // Parse Git URL to extract clean repo URL and branch
            GitUrlParser.ParsedGitUrl parsed = GitUrlParser.parse(state.getRepoUrl());
            String cleanRepoUrl = parsed.getRepoUrl();
            String branch = parsed.getBranch();
            String repoName = parsed.getRepoName();

            log.info("Parsed URL: repo={}, branch={}, name={}", cleanRepoUrl, branch, repoName);

            // ================================================================
            // CRITICAL OPTIMIZATION: Skip indexing if already indexed at current commit
            // ================================================================

            // Check current commit in repo (before cloning) - use clean URL
            String currentCommit = getCurrentCommitFromRemote(cleanRepoUrl, branch);
            String lastIndexedCommit = embeddingSyncService.getLastIndexedCommit(repoName);

            if (lastIndexedCommit != null && lastIndexedCommit.equals(currentCommit)) {
                log.info("🚀 OPTIMIZATION: Repository already indexed at current commit {}!",
                        currentCommit.substring(0, 8));
                log.info("   ⏭️  Skipping: Clone, Build, Re-indexing");
                log.info("   💰 Time saved: ~120 seconds");

                // Still need workspace for reading files during code generation
                File workspace = getOrCloneWorkspace(cleanRepoUrl, branch, repoName);
                updates.put("workspaceDir", workspace.getAbsolutePath());

                // Create a skipped result
                IndexingResult skippedResult = IndexingResult.builder()
                        .success(true)
                        .filesProcessed(0)
                        .chunksCreated(0)
                        .graphNodesCreated(0)
                        .graphEdgesCreated(0)
                        .indexedCommit(lastIndexedCommit)
                        .indexType(IndexingResult.IndexType.SKIPPED)
                        .errors(new ArrayList<>())
                        .durationMs(0L)
                        .build();

                updates.put("indexingResult", skippedResult);
                updates.put("lastAgentDecision", AgentDecision.proceed(
                        "Using cached index - no code changes since last indexing"
                ));

                return updates;
            }

            log.info("📥 New commit detected or first index - proceeding with full indexing");
            if (lastIndexedCommit != null) {
                log.info("   Previous: {}", lastIndexedCommit.substring(0, 8));
                log.info("   Current:  {}", currentCommit.substring(0, 8));
            }

            // ================================================================
            // OPTIMIZATION: Skip build for documentation tasks
            // ================================================================

            RequirementAnalysis analysis = state.getRequirementAnalysis();
            boolean isDocumentationTask = analysis != null &&
                    "documentation".equalsIgnoreCase(analysis.getTaskType());

            // ================================================================
            // STEP 1: CLONE OR REUSE WORKSPACE
            // ================================================================

            File workspace = getOrCloneWorkspace(cleanRepoUrl, branch, repoName);
            updates.put("workspaceDir", workspace.getAbsolutePath());  // CRITICAL: Store as String!

            log.info("✅ Workspace ready: {}", workspace.getAbsolutePath());

            // ================================================================
            // STEP 2: BASELINE BUILD
            // ================================================================

            // OPTIMIZATION: Skip build for documentation requests
            BuildResult baseline;
            if (isDocumentationTask) {
                log.info("📚 Documentation request - skipping build validation (saves ~60 seconds)");
                baseline = BuildResult.builder()
                        .success(true)
                        .durationMs(0L)
                        .buildLogs("Build skipped for documentation request")
                        .compilationErrors(new ArrayList<>())
                        .build();
            } else {
                log.info("🔨 Running baseline build...");
                baseline = buildService.buildAndVerify(workspace);
            }

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
            // STEP 4.5: ORACLE CODE_NODES TABLE SYNC
            // ================================================================

            log.info("🔄 Syncing to Oracle CODE_NODES table...");

            // Convert Neo4j entities to JPA GraphNode entities
            List<com.purchasingpower.autoflow.model.graph.GraphNode> graphNodes = new ArrayList<>();

            // Convert classes
            for (var classNode : combinedGraph.getClasses()) {
                graphNodes.add(com.purchasingpower.autoflow.model.graph.GraphNode.builder()
                        .nodeId(classNode.getId())
                        .type(com.purchasingpower.autoflow.model.ast.ChunkType.CLASS)
                        .repoName(repoName)
                        .fullyQualifiedName(classNode.getFullyQualifiedName())
                        .simpleName(classNode.getName())
                        .packageName(classNode.getPackageName())
                        .filePath(classNode.getSourceFilePath())
                        .parentNodeId(null)
                        .summary(classNode.getJavadoc() != null ? classNode.getJavadoc() : "")
                        .lineCount(classNode.getEndLine() - classNode.getStartLine())
                        .domain(null) // Will be populated by LLM later if needed
                        .businessCapability(null)
                        .features(null)
                        .concepts(null)
                        .build());
            }

            // Convert methods
            for (var methodNode : combinedGraph.getMethods()) {
                graphNodes.add(com.purchasingpower.autoflow.model.graph.GraphNode.builder()
                        .nodeId(methodNode.getId())
                        .type(com.purchasingpower.autoflow.model.ast.ChunkType.METHOD)
                        .repoName(repoName)
                        .fullyQualifiedName(methodNode.getFullyQualifiedName())
                        .simpleName(methodNode.getName())
                        .packageName(methodNode.getClassName().substring(0,
                                methodNode.getClassName().lastIndexOf('.') > 0 ?
                                methodNode.getClassName().lastIndexOf('.') : 0))
                        .filePath(methodNode.getSourceFilePath())
                        .parentNodeId(methodNode.getClassName()) // Parent is the class
                        .summary(methodNode.getJavadoc() != null ? methodNode.getJavadoc() : "")
                        .lineCount(methodNode.getEndLine() - methodNode.getStartLine())
                        .domain(null)
                        .businessCapability(null)
                        .features(null)
                        .concepts(null)
                        .build());
            }

            // Convert fields
            for (var fieldNode : combinedGraph.getFields()) {
                graphNodes.add(com.purchasingpower.autoflow.model.graph.GraphNode.builder()
                        .nodeId(fieldNode.getId())
                        .type(com.purchasingpower.autoflow.model.ast.ChunkType.FIELD)
                        .repoName(repoName)
                        .fullyQualifiedName(fieldNode.getFullyQualifiedName())
                        .simpleName(fieldNode.getName())
                        .packageName(fieldNode.getClassName().substring(0,
                                fieldNode.getClassName().lastIndexOf('.') > 0 ?
                                fieldNode.getClassName().lastIndexOf('.') : 0))
                        .filePath(fieldNode.getSourceFilePath())
                        .parentNodeId(fieldNode.getClassName()) // Parent is the class
                        .summary(fieldNode.getJavadoc() != null ? fieldNode.getJavadoc() : "")
                        .lineCount(1)
                        .domain(null)
                        .businessCapability(null)
                        .features(null)
                        .concepts(null)
                        .build());
            }

            // Delete old nodes for this repo and save new ones
            graphNodeRepository.deleteByRepoName(repoName);
            graphNodeRepository.saveAll(graphNodes);

            log.info("✅ Oracle CODE_NODES table updated: {} nodes saved", graphNodes.size());

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
     * OPTIMIZATION: Reuses workspace and just pulls instead of re-cloning
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

                // Pull latest changes using git command
                try {
                    log.info("Pulling latest changes...");
                    ProcessBuilder pb = new ProcessBuilder("git", "pull", "origin", branch);
                    pb.directory(workspace);
                    pb.redirectErrorStream(true);

                    Process process = pb.start();
                    int exitCode = process.waitFor();

                    if (exitCode == 0) {
                        log.info("✅ Successfully pulled latest changes");
                        return workspace;
                    } else {
                        log.warn("Git pull failed with exit code: {}", exitCode);
                    }
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
     * Get current commit hash from remote repository without cloning.
     * Uses git ls-remote to query the remote HEAD.
     */
    private String getCurrentCommitFromRemote(String repoUrl, String branch) {
        try {
            log.debug("Querying remote commit for branch: {}", branch);
            ProcessBuilder pb = new ProcessBuilder(
                    "git", "ls-remote", repoUrl, "refs/heads/" + branch
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && line.length() >= 40) {
                    String commit = line.substring(0, 40);
                    log.debug("Remote HEAD at: {}", commit.substring(0, 8));
                    return commit;
                }
            }

            process.waitFor();
            log.warn("Could not determine remote commit, will proceed with full index");
            return null;

        } catch (Exception e) {
            log.warn("Failed to query remote commit: {}", e.getMessage());
            return null;  // Fail safe - proceed with full index if can't check
        }
    }
}