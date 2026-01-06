package com.purchasingpower.autoflow.knowledge.impl;

import com.purchasingpower.autoflow.core.EntityType;
import com.purchasingpower.autoflow.core.Repository;
import com.purchasingpower.autoflow.core.impl.CodeEntityImpl;
import com.purchasingpower.autoflow.core.impl.RepositoryImpl;
import com.purchasingpower.autoflow.knowledge.GraphStore;
import com.purchasingpower.autoflow.knowledge.IndexingResult;
import com.purchasingpower.autoflow.knowledge.IndexingService;
import com.purchasingpower.autoflow.knowledge.IndexingState;
import com.purchasingpower.autoflow.knowledge.IndexingStatus;
import com.purchasingpower.autoflow.knowledge.JavaParserService;
import com.purchasingpower.autoflow.model.ast.ChunkType;
import com.purchasingpower.autoflow.model.ast.CodeChunk;
import com.purchasingpower.autoflow.model.git.ParsedGitUrl;
import com.purchasingpower.autoflow.model.java.JavaClass;
import com.purchasingpower.autoflow.service.AstParserService;
import com.purchasingpower.autoflow.service.GitOperationsService;
import com.purchasingpower.autoflow.util.GitUrlParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Neo4j implementation of IndexingService.
 *
 * Orchestrates repository indexing: cloning, parsing, and storing in Neo4j.
 *
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jIndexingServiceImpl implements IndexingService {

    private final GraphStore graphStore;
    private final JavaParserService javaParserService;
    private final GitOperationsService gitOperationsService;
    private final GitUrlParser gitUrlParser;

    private final Map<String, IndexingStatus> indexingStatuses = new ConcurrentHashMap<>();

    @Override
    public IndexingResult indexRepository(Repository repository) {
        long startTime = System.currentTimeMillis();
        String repoId = repository.getId() != null ? repository.getId() : UUID.randomUUID().toString();

        log.info("Starting indexing for repository: {} ({})", repoId, repository.getUrl());
        updateStatus(repoId, IndexingState.CLONING, 5, "Cloning repository");

        Path repoPath = null;
        try {
            repoPath = cloneRepository(repository);
            updateStatus(repoId, IndexingState.PARSING, 20, "Parsing source files");

            List<File> javaFiles = findJavaFiles(repoPath);
            log.info("Found {} Java files to index", javaFiles.size());

            updateStatus(repoId, IndexingState.EXTRACTING_RELATIONSHIPS, 40, "Parsing Java classes");
            List<JavaClass> javaClasses = javaParserService.parseJavaFiles(javaFiles, repoId);
            log.info("📊 Parsed {} Java classes", javaClasses.size());

            updateStatus(repoId, IndexingState.STORING, 60, "Storing in Neo4j graph");
            graphStore.storeJavaClasses(javaClasses);

            int entitiesCreated = countEntities(javaClasses);
            int relationshipsCreated = countRelationships(javaClasses);

            storeRepositoryMetadata(repository, repoId, repoPath);

            updateStatus(repoId, IndexingState.COMPLETED, 100, "Indexing completed");

            long duration = System.currentTimeMillis() - startTime;
            log.info("Indexing completed for {}: {} entities, {} relationships in {}ms",
                repoId, entitiesCreated, relationshipsCreated, duration);

            return IndexingResultImpl.success(repoId, entitiesCreated, relationshipsCreated, duration);

        } catch (Exception e) {
            log.error("Indexing failed for repository {}: {}", repoId, e.getMessage(), e);
            updateStatus(repoId, IndexingState.FAILED, 0, "Failed: " + e.getMessage());

            long duration = System.currentTimeMillis() - startTime;
            return IndexingResultImpl.failure(e.getMessage(), duration);
        } finally {
            // Always cleanup temporary workspace
            if (repoPath != null) {
                gitOperationsService.cleanupWorkspace(repoPath.toFile());
            }
        }
    }

    @Override
    @Async
    public CompletableFuture<IndexingResult> indexRepositoryAsync(Repository repository) {
        return CompletableFuture.completedFuture(indexRepository(repository));
    }

    @Override
    public IndexingResult reindexRepository(String repositoryId) {
        log.info("Re-indexing repository: {}", repositoryId);

        var existingRepo = graphStore.getRepository(repositoryId);
        if (existingRepo.isEmpty()) {
            return IndexingResultImpl.failure("Repository not found: " + repositoryId, 0);
        }

        graphStore.deleteRepository(repositoryId);
        return indexRepository(existingRepo.get());
    }

    @Override
    public IndexingStatus getIndexingStatus(String repositoryId) {
        return indexingStatuses.getOrDefault(
            repositoryId,
            IndexingStatusImpl.notStarted(repositoryId)
        );
    }

    private void updateStatus(String repoId, IndexingState state, int progress, String step) {
        IndexingStatusImpl status = IndexingStatusImpl.builder()
            .repositoryId(repoId)
            .state(state)
            .progress(progress)
            .currentStep(step)
            .startedAt(System.currentTimeMillis())
            .build();
        indexingStatuses.put(repoId, status);
        log.debug("Indexing status: {} - {} ({}%)", repoId, step, progress);
    }

    private Path cloneRepository(Repository repository) throws Exception {
        String url = repository.getUrl();

        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("Repository URL is required");
        }

        if (isLocalPath(url)) {
            return Path.of(url);
        }

        // Parse URL to extract clean repo URL and branch (handles /tree/branch in URL)
        ParsedGitUrl parsed = gitUrlParser.parse(url);
        String cleanUrl = parsed.getRepoUrl();

        // Priority: repository.getBranch() > parsed branch from URL > "main"
        String branch = repository.getBranch();
        if (branch == null || branch.isEmpty()) {
            branch = parsed.getBranch() != null ? parsed.getBranch() : "main";
        }

        log.info("Cloning {} (branch: {})", cleanUrl, branch);
        return gitOperationsService.cloneRepository(cleanUrl, branch).toPath();
    }

    private boolean isLocalPath(String url) {
        return !url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("git@");
    }

    private List<File> findJavaFiles(Path repoPath) throws Exception {
        try (Stream<Path> walk = Files.walk(repoPath)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toString().contains("/test/"))
                .filter(p -> !p.toString().contains("\\test\\"))
                .map(Path::toFile)
                .collect(Collectors.toList());
        }
    }

    private int countEntities(List<JavaClass> javaClasses) {
        int count = 0;
        for (JavaClass javaClass : javaClasses) {
            count++; // Type node
            count += javaClass.getMethods().size(); // Method nodes
            count += javaClass.getFields().size(); // Field nodes
            count += javaClass.getAnnotations().size(); // Annotation nodes (may be de-duplicated)
        }
        return count;
    }

    private int countRelationships(List<JavaClass> javaClasses) {
        int count = 0;
        for (JavaClass javaClass : javaClasses) {
            count += javaClass.getMethods().size(); // DECLARES relationships
            count += javaClass.getFields().size(); // DECLARES relationships
            count += javaClass.getAnnotations().size(); // ANNOTATED_BY relationships

            // Count method-level relationships
            for (var method : javaClass.getMethods()) {
                count += method.getAnnotations().size(); // ANNOTATED_BY
                count += method.getMethodCalls().size(); // CALLS
            }

            // Count field-level relationships
            for (var field : javaClass.getFields()) {
                count += field.getAnnotations().size(); // ANNOTATED_BY
            }
        }
        return count;
    }

    private void storeRepositoryMetadata(Repository repository, String repoId, Path repoPath) {
        RepositoryImpl repoToStore = RepositoryImpl.builder()
            .id(repoId)
            .url(repository.getUrl())
            .branch(repository.getBranch())
            .type(repository.getType())
            .language(repository.getLanguage() != null ? repository.getLanguage() : "Java")
            .domain(repository.getDomain())
            .lastIndexedAt(LocalDateTime.now())
            .build();
        graphStore.storeRepository(repoToStore);
    }

    private EntityType mapChunkType(ChunkType chunkType) {
        if (chunkType == null) {
            return EntityType.CLASS;
        }
        return switch (chunkType) {
            case METHOD, CONSTRUCTOR -> EntityType.METHOD;
            case INTERFACE -> EntityType.INTERFACE;
            case ENUM -> EntityType.ENUM;
            default -> EntityType.CLASS;
        };
    }
}
