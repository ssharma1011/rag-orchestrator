package com.purchasingpower.autoflow.service.impl;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.configuration.AppProperties;
import com.purchasingpower.autoflow.model.ast.CodeChunk;
import com.purchasingpower.autoflow.model.sync.ChangedFile;
import com.purchasingpower.autoflow.model.sync.ChangedFile.ChangeType;
import com.purchasingpower.autoflow.model.sync.EmbeddingSyncResult;
import com.purchasingpower.autoflow.model.sync.SyncType;
import com.purchasingpower.autoflow.service.AstParserService;
import com.purchasingpower.autoflow.service.IncrementalEmbeddingSyncService;
import io.pinecone.clients.Pinecone;
import io.pinecone.unsigned_indices_model.VectorWithUnsignedIndices;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Production-ready incremental embedding sync service.
 *
 * CRITICAL DESIGN DECISIONS:
 * ════════════════════════════════════════════════════════════════════════════
 *
 * 1. COMMIT TRACKING IN PINECONE:
 * - Stored as a special vector with ID: "__metadata__:{repoName}:index_state"
 * - Contains: last_indexed_commit, timestamp, stats
 * - Survives server restarts, works across workspaces
 *
 * 2. FILE-LEVEL GRANULARITY:
 * - When file changes, ALL chunks for that file are deleted and re-created
 * - Uses file_path metadata field for filtering
 * - One file = 1 CLASS + N METHODs + M FIELDs chunks
 *
 * 3. GIT DIFF FOR CHANGE DETECTION:
 * - Uses JGit to compare trees between commits
 * - Handles: ADD, MODIFY, DELETE, RENAME
 * - Only processes .java files in src/main/java
 *
 * ════════════════════════════════════════════════════════════════════════════
 */
@Slf4j
@Service
public class IncrementalEmbeddingSyncServiceImpl implements IncrementalEmbeddingSyncService {

    private static final String METADATA_VECTOR_PREFIX = "__metadata__:";
    private static final String INDEX_STATE_SUFFIX = ":index_state";
    private static final int EMBEDDING_DIMENSION = 768;

    private final AstParserService astParser;
    private final GeminiClient geminiClient;
    private final Pinecone pineconeClient;
    private final String indexName;

    public IncrementalEmbeddingSyncServiceImpl(
            AstParserService astParser,
            GeminiClient geminiClient,
            AppProperties props) {
        this.astParser = astParser;
        this.geminiClient = geminiClient;
        this.pineconeClient = new Pinecone.Builder(props.getPinecone().getApiKey()).build();
        this.indexName = props.getPinecone().getIndexName();
    }

    @Override
    public EmbeddingSyncResult syncEmbeddings(File workspaceDir, String repoName) {
        long startTime = System.currentTimeMillis();
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("Starting embedding sync for repository: {}", repoName);
        log.info("═══════════════════════════════════════════════════════════════");

        try {
            // 1. Get current commit from workspace
            String currentCommit = getCurrentCommit(workspaceDir);
            log.info("Current HEAD commit: {}", currentCommit.substring(0, 8));

            // 2. Get last indexed commit from Pinecone
            String lastIndexedCommit = getLastIndexedCommit(repoName);

            // 3. Decide: Full index or incremental?
            if (lastIndexedCommit == null) {
                log.info("No previous index found. Performing INITIAL FULL INDEX.");
                return performFullIndex(workspaceDir, repoName, currentCommit,
                        SyncType.INITIAL_FULL_INDEX);
            }

            if (lastIndexedCommit.equals(currentCommit)) {
                log.info("Already indexed at commit {}. No changes.", currentCommit.substring(0, 8));
                return EmbeddingSyncResult.builder()
                        .syncType(SyncType.NO_CHANGES)
                        .filesAnalyzed(0)
                        .filesChanged(0)
                        .chunksDeleted(0)
                        .chunksCreated(0)
                        .embeddingTimeMs(0)
                        .totalTimeMs(System.currentTimeMillis() - startTime)
                        .fromCommit(lastIndexedCommit)
                        .toCommit(currentCommit)
                        .build();
            }

            // 4. Get changed files
            log.info("Finding changes: {}..{}",
                    lastIndexedCommit.substring(0, 8), currentCommit.substring(0, 8));

            List<ChangedFile> changedFiles = getChangedFiles(workspaceDir, lastIndexedCommit, currentCommit);

            if (changedFiles.isEmpty()) {
                log.info("No Java source files changed.");
                // Still update the commit reference
                updateIndexState(repoName, currentCommit, 0);
                return EmbeddingSyncResult.builder()
                        .syncType(SyncType.NO_CHANGES)
                        .filesAnalyzed(0)
                        .filesChanged(0)
                        .chunksDeleted(0)
                        .chunksCreated(0)
                        .embeddingTimeMs(0)
                        .totalTimeMs(System.currentTimeMillis() - startTime)
                        .fromCommit(lastIndexedCommit)
                        .toCommit(currentCommit)
                        .build();
            }

            log.info("Found {} changed Java files:", changedFiles.size());
            for (ChangedFile cf : changedFiles) {
                log.info("  {} {}", cf.changeType(), cf.path());
            }

            // 5. Perform incremental sync
            return performIncrementalSync(workspaceDir, repoName, changedFiles,
                    lastIndexedCommit, currentCommit, startTime);

        } catch (Exception e) {
            log.error("Embedding sync failed", e);
            return EmbeddingSyncResult.builder()
                    .syncType(SyncType.ERROR)
                    .filesAnalyzed(0)
                    .filesChanged(0)
                    .chunksDeleted(0)
                    .chunksCreated(0)
                    .embeddingTimeMs(0)
                    .totalTimeMs(System.currentTimeMillis() - startTime)
                    .fromCommit(null)
                    .toCommit(null)
                    .build();
        }
    }

    @Override
    public EmbeddingSyncResult forceFullReindex(File workspaceDir, String repoName) {
        log.warn("FORCED FULL REINDEX requested for {}", repoName);
        String currentCommit = getCurrentCommit(workspaceDir);
        return performFullIndex(workspaceDir, repoName, currentCommit,
                SyncType.FORCED_FULL_REINDEX);
    }

    @Override
    public String getLastIndexedCommit(String repoName) {
        String metadataId = METADATA_VECTOR_PREFIX + repoName + INDEX_STATE_SUFFIX;

        // Retry up to 3 times with exponential backoff (2s, 4s, 8s) for SERVER ERRORS ONLY
        for (int attempt = 1; attempt <= 3; attempt++) {
            var callCtx = com.purchasingpower.autoflow.util.ExternalCallLogger.startCall(
                    com.purchasingpower.autoflow.util.ExternalCallLogger.ServiceType.PINECONE,
                    "FetchMetadata",
                    log
            );

            try {
                callCtx.logRequest("Fetching metadata",
                        "Vector ID", metadataId,
                        "Attempt", attempt + "/3");

                var response = pineconeClient.getIndexConnection(indexName)
                        .fetch(List.of(metadataId), "");

                // Empty response is VALID - means no metadata exists (expected for fresh index)
                // Do NOT retry on empty response - return immediately
                if (response == null || response.getVectorsMap().isEmpty()) {
                    callCtx.logResponse("No metadata found (fresh index)");
                    log.info("No previous index state found - will perform full index");
                    return null;
                }

                var vector = response.getVectorsMap().get(metadataId);
                if (vector == null) {
                    callCtx.logResponse("Vector not found in response",
                            "Available IDs", response.getVectorsMap().keySet());
                    log.warn("Metadata vector ID mismatch");
                    return null;
                }

                var metadata = vector.getMetadata().getFieldsMap();
                callCtx.logResponse("Metadata found",
                        "Fields", metadata.keySet());

                if (metadata.containsKey("last_indexed_commit")) {
                    String commit = metadata.get("last_indexed_commit").getStringValue();
                    log.info("✅ Found previous commit: {}",
                            commit.substring(0, Math.min(8, commit.length())));
                    return commit;
                }

                log.warn("⚠️ Metadata vector found but missing 'last_indexed_commit' field");
                return null;

            } catch (Exception e) {
                // ONLY retry on server-side errors (502, 503, etc.)
                // ✅ FIX: PineconeUnmappedHttpException doesn't exist - use generic Exception
                String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                boolean isServerError = errorMsg.contains("502") ||
                                       errorMsg.contains("503") ||
                                       errorMsg.contains("504");

                if (isServerError && attempt < 3) {
                    long delayMs = (long) Math.pow(2, attempt) * 1000;
                    callCtx.logError("Server error (retryable) - attempt " + attempt, e);
                    log.warn("⚠️ Pinecone server error, retrying in {}ms...", delayMs);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    continue; // Retry
                }

                // Non-retryable error or retries exhausted
                callCtx.logError("HTTP error (non-retryable or retries exhausted)", e);
                log.error("❌ Failed to fetch last indexed commit: {}", e.getMessage());
                return null;

            } catch (Exception e) {
                callCtx.logError("Unexpected error", e);
                log.error("❌ Failed to fetch last indexed commit: {}", e.getMessage(), e);
                return null;
            }
        }

        // All retries exhausted (should only reach here if server errors persisted)
        log.error("⚠️ All retry attempts exhausted due to persistent server errors");
        return null;
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PRIVATE METHODS
    // ════════════════════════════════════════════════════════════════════════════

    private EmbeddingSyncResult performFullIndex(
            File workspaceDir, String repoName, String currentCommit,
            SyncType syncType) {

        long startTime = System.currentTimeMillis();

        // 1. Delete all existing vectors for this repo
        log.info("Deleting all existing vectors for repo: {}", repoName);
        deleteAllVectorsForRepo(repoName);

        // 2. Find all Java files
        List<File> allFiles = findAllJavaFiles(workspaceDir);
        log.info("Found {} Java files to index", allFiles.size());

        if (allFiles.isEmpty()) {
            updateIndexState(repoName, currentCommit, 0);
            return EmbeddingSyncResult.builder()
                    .syncType(syncType)
                    .filesAnalyzed(0)
                    .filesChanged(0)
                    .chunksDeleted(0)
                    .chunksCreated(0)
                    .embeddingTimeMs(0)
                    .totalTimeMs(System.currentTimeMillis() - startTime)
                    .fromCommit(null)
                    .toCommit(currentCommit)
                    .build();
        }

        // 3. Parse all files
        List<CodeChunk> allChunks = parseFiles(allFiles, repoName);
        log.info("Parsed {} code chunks from {} files", allChunks.size(), allFiles.size());

        // 4. Embed and upsert
        long embedStart = System.currentTimeMillis();
        int created = embedAndUpsertChunks(allChunks);
        long embedTime = System.currentTimeMillis() - embedStart;

        // 5. Update index state
        updateIndexState(repoName, currentCommit, created);

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Full index complete: {} chunks in {}ms", created, totalTime);

        return EmbeddingSyncResult.builder()
                .syncType(syncType)
                .filesAnalyzed(allFiles.size())
                .filesChanged(allFiles.size())
                .chunksDeleted(0) // Nothing deleted (or unknown for first run)
                .chunksCreated(created)
                .embeddingTimeMs(embedTime)
                .totalTimeMs(totalTime)
                .fromCommit(null)
                .toCommit(currentCommit)
                .build();
    }

    private EmbeddingSyncResult performIncrementalSync(
            File workspaceDir, String repoName, List<ChangedFile> changedFiles,
            String fromCommit, String toCommit, long startTime) {

        int totalDeleted = 0;
        List<CodeChunk> chunksToCreate = new ArrayList<>();

        // Process each changed file
        for (ChangedFile change : changedFiles) {
            String filePath = change.path();

            // DELETE or MODIFY: Remove old vectors for this file
            if (change.changeType() == ChangeType.DELETE || change.changeType() == ChangeType.MODIFY) {
                int deleted = deleteVectorsForFile(repoName, filePath);
                totalDeleted += deleted;
                log.debug("Deleted {} vectors for: {}", deleted, filePath);
            }

            // ADD or MODIFY: Parse and collect new chunks
            if (change.changeType() == ChangeType.ADD || change.changeType() == ChangeType.MODIFY) {
                File file = new File(workspaceDir, filePath);
                if (file.exists()) {
                    try {
                        List<CodeChunk> chunks = astParser.parseJavaFile(file, repoName);
                        chunksToCreate.addAll(chunks);
                        log.debug("Parsed {} chunks from: {}", chunks.size(), filePath);
                    } catch (Exception e) {
                        log.warn("Failed to parse {}: {}", filePath, e.getMessage());
                    }
                }
            }
        }

        // Embed and upsert all new chunks in one batch
        long embedStart = System.currentTimeMillis();
        int created = 0;
        if (!chunksToCreate.isEmpty()) {
            created = embedAndUpsertChunks(chunksToCreate);
        }
        long embedTime = System.currentTimeMillis() - embedStart;

        // Update index state
        updateIndexState(repoName, toCommit, created);

        long totalTime = System.currentTimeMillis() - startTime;

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("Incremental sync complete:");
        log.info("  Files changed: {}", changedFiles.size());
        log.info("  Chunks deleted: {}", totalDeleted);
        log.info("  Chunks created: {}", created);
        log.info("  Embedding time: {}ms", embedTime);
        log.info("  Total time: {}ms", totalTime);
        log.info("═══════════════════════════════════════════════════════════════");

        return EmbeddingSyncResult.builder()
                .syncType(SyncType.INCREMENTAL)
                .filesAnalyzed(changedFiles.size())
                .filesChanged(changedFiles.size())
                .chunksDeleted(totalDeleted)
                .chunksCreated(created)
                .embeddingTimeMs(embedTime)
                .totalTimeMs(totalTime)
                .fromCommit(fromCommit)
                .toCommit(toCommit)
                .build();
    }

    /**
     * Gets list of changed Java files between two commits.
     */
    private List<ChangedFile> getChangedFiles(File workspaceDir, String fromCommit, String toCommit) {
        List<ChangedFile> result = new ArrayList<>();

        try (Git git = Git.open(workspaceDir)) {
            Repository repo = git.getRepository();

            ObjectId oldTree = repo.resolve(fromCommit + "^{tree}");
            ObjectId newTree = repo.resolve(toCommit + "^{tree}");

            if (oldTree == null || newTree == null) {
                log.warn("Could not resolve commit trees. Returning empty list.");
                return result;
            }

            try (ObjectReader reader = repo.newObjectReader()) {
                CanonicalTreeParser oldParser = new CanonicalTreeParser();
                oldParser.reset(reader, oldTree);

                CanonicalTreeParser newParser = new CanonicalTreeParser();
                newParser.reset(reader, newTree);

                List<DiffEntry> diffs = git.diff()
                        .setOldTree(oldParser)
                        .setNewTree(newParser)
                        .call();

                for (DiffEntry diff : diffs) {
                    String path;
                    ChangeType changeType;

                    switch (diff.getChangeType()) {
                        case ADD:
                            path = diff.getNewPath();
                            changeType = ChangeType.ADD;
                            break;
                        case DELETE:
                            path = diff.getOldPath();
                            changeType = ChangeType.DELETE;
                            break;
                        case MODIFY:
                            path = diff.getNewPath();
                            changeType = ChangeType.MODIFY;
                            break;
                        case RENAME:
                            // Handle rename as DELETE old + ADD new
                            if (isJavaSourceFile(diff.getOldPath())) {
                                result.add(new ChangedFile(diff.getOldPath(), ChangeType.DELETE));
                            }
                            path = diff.getNewPath();
                            changeType = ChangeType.ADD;
                            break;
                        case COPY:
                            path = diff.getNewPath();
                            changeType = ChangeType.ADD;
                            break;
                        default:
                            continue;
                    }

                    // Only include Java source files (not tests)
                    if (isJavaSourceFile(path)) {
                        result.add(new ChangedFile(path, changeType));
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to get changed files: {}", e.getMessage());
        }

        return result;
    }

    private boolean isJavaSourceFile(String path) {
        return path.endsWith(".java")
                && path.contains("src/main/java")
                && !path.contains("src/test/");
    }

    /**
     * Deletes all CODE vectors for a repository.
     * IMPORTANT: Does NOT delete metadata vector (preserves index state).
     */
    private void deleteAllVectorsForRepo(String repoName) {
        var callCtx = com.purchasingpower.autoflow.util.ExternalCallLogger.startCall(
                com.purchasingpower.autoflow.util.ExternalCallLogger.ServiceType.PINECONE,
                "DeleteByFilter",
                log
        );

        try {
            // Delete ONLY code vectors (exclude metadata by filtering on type != INDEX_METADATA)
            // Build filter: repo_name = repoName AND type != "INDEX_METADATA"
            Struct filter = Struct.newBuilder()
                    .putFields("$and", Value.newBuilder()
                            .setListValue(com.google.protobuf.ListValue.newBuilder()
                                    .addValues(Value.newBuilder()
                                            .setStructValue(Struct.newBuilder()
                                                    .putFields("repo_name", Value.newBuilder()
                                                            .setStructValue(Struct.newBuilder()
                                                                    .putFields("$eq", Value.newBuilder()
                                                                            .setStringValue(repoName)
                                                                            .build())
                                                                    .build())
                                                            .build())
                                                    .build())
                                            .build())
                                    .addValues(Value.newBuilder()
                                            .setStructValue(Struct.newBuilder()
                                                    .putFields("type", Value.newBuilder()
                                                            .setStructValue(Struct.newBuilder()
                                                                    .putFields("$ne", Value.newBuilder()
                                                                            .setStringValue("INDEX_METADATA")
                                                                            .build())
                                                                    .build())
                                                            .build())
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build();

            callCtx.logRequest("Deleting all code vectors",
                    "Repo", repoName,
                    "Filter", "repo_name=" + repoName + " AND type!=INDEX_METADATA");

            pineconeClient.getIndexConnection(indexName).deleteByFilter(filter, "");

            callCtx.logResponse("Code vectors deleted (metadata preserved)");

        } catch (io.grpc.StatusRuntimeException e) {
            // Handle "Namespace not found" gracefully - it's okay if nothing exists to delete
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                callCtx.logResponse("Nothing to delete (namespace doesn't exist yet)");
                log.info("Namespace not found - skipping delete (fresh index)");
            } else {
                callCtx.logError("gRPC error during delete", e);
                throw new RuntimeException("Failed to delete vectors for repo: " + repoName, e);
            }
        } catch (Exception e) {
            callCtx.logError("Failed to delete vectors", e);
            throw new RuntimeException("Failed to delete vectors for repo: " + repoName, e);
        }
    }

    /**
     * Deletes all vectors for a specific file.
     * Returns estimated count (Pinecone doesn't return actual count).
     */
    private int deleteVectorsForFile(String repoName, String filePath) {
        var callCtx = com.purchasingpower.autoflow.util.ExternalCallLogger.startCall(
                com.purchasingpower.autoflow.util.ExternalCallLogger.ServiceType.PINECONE,
                "DeleteByFilter",
                log
        );

        try {
            // Normalize the file path
            String normalizedPath = filePath.replace("\\", "/");

            Struct filter = Struct.newBuilder()
                    .putFields("repo_name", Value.newBuilder()
                            .setStructValue(Struct.newBuilder()
                                    .putFields("$eq", Value.newBuilder()
                                            .setStringValue(repoName)
                                            .build())
                                    .build())
                            .build())
                    .putFields("file_path", Value.newBuilder()
                            .setStructValue(Struct.newBuilder()
                                    .putFields("$eq", Value.newBuilder()
                                            .setStringValue(normalizedPath)
                                            .build())
                                    .build())
                            .build())
                    .build();

            callCtx.logRequest("Deleting vectors for file",
                    "Repo", repoName,
                    "File", normalizedPath);

            pineconeClient.getIndexConnection(indexName).deleteByFilter(filter, "");

            callCtx.logResponse("File vectors deleted (estimated ~5 chunks)");

            // Pinecone doesn't return count, estimate ~5 chunks per file
            return 5;

        } catch (Exception e) {
            callCtx.logError("Failed to delete file vectors", e);
            return 0;
        }
    }

    /**
     * Parses Java files into CodeChunks.
     */
    private List<CodeChunk> parseFiles(List<File> files, String repoName) {
        List<CodeChunk> allChunks = new ArrayList<>();

        for (File file : files) {
            try {
                List<CodeChunk> chunks = astParser.parseJavaFile(file, repoName);
                allChunks.addAll(chunks);
            } catch (Exception e) {
                log.warn("Failed to parse {}: {}", file.getName(), e.getMessage());
            }
        }

        return allChunks;
    }

    /**
     * Embeds code chunks and upserts to Pinecone.
     */
    private int embedAndUpsertChunks(List<CodeChunk> chunks) {
        if (chunks.isEmpty()) {
            return 0;
        }

        // 1. Extract content
        List<String> contents = chunks.stream()
                .map(CodeChunk::getContent)
                .toList();

        // 2. Batch embed
        log.info("Creating embeddings for {} chunks...", contents.size());
        List<List<Double>> embeddings = geminiClient.batchCreateEmbeddings(contents);

        if (embeddings.size() != chunks.size()) {
            throw new RuntimeException("Embedding count mismatch: expected "
                    + chunks.size() + ", got " + embeddings.size());
        }

        // 3. Build vectors
        List<VectorWithUnsignedIndices> vectors = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            CodeChunk chunk = chunks.get(i);
            List<Float> floatVector = embeddings.get(i).stream()
                    .map(Double::floatValue)
                    .toList();

            Map<String, String> flatMetadata = chunk.toFlatMetadata();
            Struct.Builder metadataBuilder = Struct.newBuilder();
            for (Map.Entry<String, String> entry : flatMetadata.entrySet()) {
                metadataBuilder.putFields(
                        entry.getKey(),
                        Value.newBuilder().setStringValue(entry.getValue()).build()
                );
            }

            vectors.add(new VectorWithUnsignedIndices(
                    chunk.getId(),
                    floatVector,
                    metadataBuilder.build(),
                    null
            ));
        }

        // 4. Upsert in batches
        int batchSize = 100;
        int batchCount = (int) Math.ceil((double) vectors.size() / batchSize);

        for (int i = 0; i < vectors.size(); i += batchSize) {
            int end = Math.min(i + batchSize, vectors.size());
            List<VectorWithUnsignedIndices> batch = vectors.subList(i, end);
            int batchNum = (i / batchSize) + 1;

            var callCtx = com.purchasingpower.autoflow.util.ExternalCallLogger.startCall(
                    com.purchasingpower.autoflow.util.ExternalCallLogger.ServiceType.PINECONE,
                    "Upsert",
                    log
            );

            try {
                callCtx.logRequest("Upserting vector batch",
                        "Batch", batchNum + "/" + batchCount,
                        "Vectors", batch.size());

                pineconeClient.getIndexConnection(indexName).upsert(batch, "");

                callCtx.logResponse("Batch upserted successfully");
            } catch (Exception e) {
                callCtx.logError("Failed to upsert batch", e);
                throw new RuntimeException("Failed to upsert batch " + batchNum, e);
            }
        }

        log.info("✅ Total upserted: {} vectors in {} batches", vectors.size(), batchCount);
        return vectors.size();
    }

    /**
     * Updates the index state metadata in Pinecone.
     */
    private void updateIndexState(String repoName, String commitHash, int vectorCount) {
        String metadataId = METADATA_VECTOR_PREFIX + repoName + INDEX_STATE_SUFFIX;

        // Upsert metadata vector
        var upsertCtx = com.purchasingpower.autoflow.util.ExternalCallLogger.startCall(
                com.purchasingpower.autoflow.util.ExternalCallLogger.ServiceType.PINECONE,
                "UpsertMetadata",
                log
        );

        try {
            // Create dummy vector with small non-zero values (Pinecone rejects all-zero vectors)
            List<Float> dummyVector = new ArrayList<>(Collections.nCopies(EMBEDDING_DIMENSION, 0.001f));

            Struct metadata = Struct.newBuilder()
                    .putFields("type", Value.newBuilder()
                            .setStringValue("INDEX_METADATA").build())
                    .putFields("repo_name", Value.newBuilder()
                            .setStringValue(repoName).build())
                    .putFields("last_indexed_commit", Value.newBuilder()
                            .setStringValue(commitHash).build())
                    .putFields("last_indexed_at", Value.newBuilder()
                            .setStringValue(Instant.now().toString()).build())
                    .putFields("vector_count", Value.newBuilder()
                            .setStringValue(String.valueOf(vectorCount)).build())
                    .build();

            VectorWithUnsignedIndices metadataVector = new VectorWithUnsignedIndices(
                    metadataId,
                    dummyVector,
                    metadata,
                    null
            );

            upsertCtx.logRequest("Saving index state",
                    "Repo", repoName,
                    "Commit", commitHash.substring(0, Math.min(8, commitHash.length())),
                    "Vector Count", vectorCount);

            pineconeClient.getIndexConnection(indexName).upsert(List.of(metadataVector), "");

            upsertCtx.logResponse("Metadata vector saved");

            // Verify the save by fetching it back (handles eventual consistency)
            Thread.sleep(1000); // Wait 1 second for Pinecone to process

            var verifyCtx = com.purchasingpower.autoflow.util.ExternalCallLogger.startCall(
                    com.purchasingpower.autoflow.util.ExternalCallLogger.ServiceType.PINECONE,
                    "VerifyMetadata",
                    log
            );

            try {
                verifyCtx.logRequest("Verifying metadata save", "Vector ID", metadataId);

                var verifyResponse = pineconeClient.getIndexConnection(indexName).fetch(List.of(metadataId), "");

                if (verifyResponse != null && !verifyResponse.getVectorsMap().isEmpty()) {
                    verifyCtx.logResponse("Metadata verified successfully");
                } else {
                    verifyCtx.logResponse("Metadata not immediately retrievable (eventual consistency)");
                    log.warn("⚠️ Metadata vector saved but not immediately retrievable");
                }
            } catch (Exception e) {
                verifyCtx.logError("Verification failed", e);
                log.warn("⚠️ Could not verify metadata save: {}", e.getMessage());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            upsertCtx.logError("Interrupted during verification wait", e);
            throw new RuntimeException("Interrupted while saving metadata", e);
        } catch (Exception e) {
            upsertCtx.logError("Failed to save metadata", e);
            throw new RuntimeException("Failed to save metadata vector to Pinecone", e);
        }
    }

    /**
     * Gets current HEAD commit hash.
     */
    private String getCurrentCommit(File workspaceDir) {
        try (Git git = Git.open(workspaceDir)) {
            ObjectId head = git.getRepository().resolve("HEAD");
            return head != null ? head.getName() : "unknown";
        } catch (Exception e) {
            log.error("Failed to get current commit: {}", e.getMessage());
            return "unknown";
        }
    }

    /**
     * Finds all Java source files (excludes tests).
     */
    private List<File> findAllJavaFiles(File root) {
        try (Stream<Path> walk = Files.walk(root.toPath())) {
            return walk
                    .filter(p -> !Files.isDirectory(p))
                    .map(Path::toFile)
                    .filter(f -> f.getName().endsWith(".java"))
                    .filter(f -> f.getAbsolutePath().contains("src" + File.separator + "main"))
                    .filter(f -> !f.getAbsolutePath().contains("src" + File.separator + "test"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to find Java files: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}