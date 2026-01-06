package com.purchasingpower.autoflow.api;

import com.purchasingpower.autoflow.core.impl.RepositoryImpl;
import com.purchasingpower.autoflow.knowledge.GraphStore;
import com.purchasingpower.autoflow.knowledge.IndexingResult;
import com.purchasingpower.autoflow.knowledge.IndexingService;
import com.purchasingpower.autoflow.knowledge.IndexingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for knowledge/indexing API.
 *
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class KnowledgeController {

    private final IndexingService indexingService;
    private final GraphStore graphStore;

    /**
     * Index a repository.
     *
     * POST /api/v1/index/repo
     */
    @PostMapping("/index/repo")
    public ResponseEntity<IndexResponse> indexRepo(@RequestBody IndexRequest request) {
        try {
            if (request.getRepoUrl() == null || request.getRepoUrl().isBlank()) {
                return ResponseEntity.badRequest()
                    .body(IndexResponse.error("Repository URL is required"));
            }

            log.info("Indexing repository: {}", request.getRepoUrl());

            RepositoryImpl repo = RepositoryImpl.builder()
                .url(request.getRepoUrl())
                .branch(request.getBranch() != null ? request.getBranch() : "main")
                .language(request.getLanguage() != null ? request.getLanguage() : "Java")
                .domain(request.getDomain())
                .build();

            IndexingResult result = indexingService.indexRepository(repo);

            if (result.isSuccess()) {
                return ResponseEntity.ok(IndexResponse.success(
                    repo.getId(),
                    result.getEntitiesCreated(),
                    result.getRelationshipsCreated(),
                    result.getDurationMs()
                ));
            } else {
                return ResponseEntity.ok(IndexResponse.error(
                    "Indexing failed: " + String.join(", ", result.getErrors())
                ));
            }

        } catch (Exception e) {
            log.error("Indexing failed", e);
            return ResponseEntity.internalServerError()
                .body(IndexResponse.error("Indexing failed: " + e.getMessage()));
        }
    }

    /**
     * Get indexing status.
     *
     * GET /api/v1/index/{repoId}/status
     */
    @GetMapping("/index/{repoId}/status")
    public ResponseEntity<IndexingStatus> getIndexingStatus(@PathVariable String repoId) {
        try {
            IndexingStatus status = indexingService.getIndexingStatus(repoId);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Failed to get indexing status", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get repository structure.
     *
     * GET /api/v1/repos/{id}/structure
     */
    @GetMapping("/repos/{repoId}/structure")
    public ResponseEntity<List<Map<String, Object>>> getRepoStructure(@PathVariable String repoId) {
        try {
            String cypher = """
                MATCH (c:Class {repositoryId: $repoId})
                OPTIONAL MATCH (c)-[:HAS_MEMBER]->(m:Method)
                RETURN c.name as className, c.fullyQualifiedName as fqn,
                       collect(m.name) as methods
                ORDER BY c.name
                """;

            List<Map<String, Object>> structure = graphStore.executeCypherQueryRaw(
                cypher, Map.of("repoId", repoId)
            );

            return ResponseEntity.ok(structure);

        } catch (Exception e) {
            log.error("Failed to get repo structure", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get cross-repository topology.
     *
     * GET /api/v1/topology
     */
    @GetMapping("/topology")
    public ResponseEntity<List<Map<String, Object>>> getTopology() {
        try {
            String cypher = """
                MATCH (s1:Service)-[r:CALLS]->(s2:Service)
                RETURN s1.name as source, s2.name as target, r.via as via
                """;

            List<Map<String, Object>> topology = graphStore.executeCypherQueryRaw(cypher, Map.of());
            return ResponseEntity.ok(topology);

        } catch (Exception e) {
            log.error("Failed to get topology", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
