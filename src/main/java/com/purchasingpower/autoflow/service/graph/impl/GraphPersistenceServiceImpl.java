package com.purchasingpower.autoflow.service.graph.impl;

import com.purchasingpower.autoflow.model.ast.ClassMetadata;
import com.purchasingpower.autoflow.model.ast.CodeChunk;
import com.purchasingpower.autoflow.model.ast.DependencyEdge;
import com.purchasingpower.autoflow.model.graph.GraphEdge;
import com.purchasingpower.autoflow.model.graph.GraphNode;
import com.purchasingpower.autoflow.repository.GraphEdgeRepository;
import com.purchasingpower.autoflow.repository.GraphNodeRepository;
import com.purchasingpower.autoflow.service.graph.GraphPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Production-grade graph persistence service.
 * Converts AST-parsed CodeChunks into Oracle graph entities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphPersistenceServiceImpl implements GraphPersistenceService {

    private final GraphNodeRepository nodeRepository;
    private final GraphEdgeRepository edgeRepository;

    @Override
    @Transactional
    public void persistChunks(List<CodeChunk> chunks, String repoName) {
        if (chunks == null || chunks.isEmpty()) {
            log.warn("No chunks to persist for repo: {}", repoName);
            return;
        }

        log.info("Starting graph persistence for repo: {} ({} chunks)", repoName, chunks.size());

        try {
            // Step 1: Convert chunks to nodes
            List<GraphNode> nodes = convertToNodes(chunks, repoName);
            
            // Step 2: Extract edges from chunks
            List<GraphEdge> edges = extractEdges(chunks, repoName);

            // Step 3: Delete old data for this repo (idempotent update)
            log.debug("Deleting old graph data for repo: {}", repoName);
            edgeRepository.deleteByRepoName(repoName);
            nodeRepository.deleteByRepoName(repoName);

            // Step 4: Bulk insert nodes
            log.debug("Inserting {} nodes", nodes.size());
            nodeRepository.saveAll(nodes);

            // Step 5: Bulk insert edges
            log.debug("Inserting {} edges", edges.size());
            edgeRepository.saveAll(edges);

            log.info("✅ Graph persistence complete: {} nodes, {} edges for repo: {}",
                    nodes.size(), edges.size(), repoName);

        } catch (Exception e) {
            log.error("Graph persistence failed for repo: {}", repoName, e);
            throw new RuntimeException("Failed to persist graph: " + e.getMessage(), e);
        }
    }

    /**
     * Converts CodeChunks to GraphNode entities.
     * Parent chunks (CLASS) become class nodes, child chunks (METHOD) become method nodes.
     */
    private List<GraphNode> convertToNodes(List<CodeChunk> chunks, String repoName) {
        return chunks.stream()
                .map(chunk -> {
                    GraphNode.GraphNodeBuilder builder = GraphNode.builder()
                            .nodeId(chunk.getId())
                            .type(chunk.getType())
                            .repoName(repoName)
                            .fullyQualifiedName(extractFQN(chunk))
                            .simpleName(extractSimpleName(chunk))
                            .packageName(extractPackageName(chunk))
                            .filePath(extractFilePath(chunk))
                            .parentNodeId(chunk.getParentChunkId())
                            .summary(buildNodeSummary(chunk))
                            .lineCount(extractLineCount(chunk));

                    // Add knowledge graph fields from ClassMetadata
                    if (chunk.getClassMetadata() != null) {
                        ClassMetadata meta = chunk.getClassMetadata();
                        builder.domain(meta.getDomain())
                                .businessCapability(meta.getBusinessCapability())
                                .features(meta.getFeatures() != null ?
                                        String.join(",", meta.getFeatures()) : null)
                                .concepts(meta.getConcepts() != null ?
                                        String.join(",", meta.getConcepts()) : null);
                    }

                    return builder.build();
                })
                .collect(Collectors.toList());
    }
    /**
     * Extracts dependency edges from CodeChunks.
     * Each DependencyEdge becomes a GraphEdge.
     */
    private List<GraphEdge> extractEdges(List<CodeChunk> chunks, String repoName) {
        List<GraphEdge> allEdges = new ArrayList<>();

        for (CodeChunk chunk : chunks) {
            // Extract class-level dependencies
            if (chunk.getClassMetadata() != null && chunk.getClassMetadata().getDependencies() != null) {
                for (DependencyEdge dep : chunk.getClassMetadata().getDependencies()) {
                    allEdges.add(GraphEdge.builder()
                            .sourceNodeId(chunk.getId())
                            .targetNodeId(repoName + ":" + dep.getTargetClass())
                            .relationshipType(dep.getType())
                            .cardinality(dep.getCardinality())
                            .context(dep.getContext())
                            .repoName(repoName)
                            .build());
                }
            }

            // Extract method-level dependencies (method calls)
            if (chunk.getMethodMetadata() != null && chunk.getMethodMetadata().getMethodCalls() != null) {
                for (DependencyEdge dep : chunk.getMethodMetadata().getMethodCalls()) {
                    allEdges.add(GraphEdge.builder()
                            .sourceNodeId(chunk.getId())
                            .targetNodeId(repoName + ":" + dep.getTargetClass())
                            .relationshipType(dep.getType())
                            .cardinality(dep.getCardinality())
                            .context(dep.getContext())
                            .repoName(repoName)
                            .build());
                }
            }
        }

        return allEdges;
    }

    // ===================================================================
    // Helper Methods - Extract data from CodeChunk
    // ===================================================================

    private String extractFQN(CodeChunk chunk) {
        if (chunk.getClassMetadata() != null) {
            return chunk.getClassMetadata().getFullyQualifiedName();
        } else if (chunk.getMethodMetadata() != null) {
            return chunk.getMethodMetadata().getFullyQualifiedName();
        }
        return chunk.getId();
    }

    private String extractSimpleName(CodeChunk chunk) {
        if (chunk.getClassMetadata() != null) {
            return chunk.getClassMetadata().getClassName();
        } else if (chunk.getMethodMetadata() != null) {
            return chunk.getMethodMetadata().getMethodName();
        }
        return "unknown";
    }

    private String extractPackageName(CodeChunk chunk) {
        if (chunk.getClassMetadata() != null) {
            return chunk.getClassMetadata().getPackageName();
        } else if (chunk.getMethodMetadata() != null) {
            String owningClass = chunk.getMethodMetadata().getOwningClass();
            int lastDot = owningClass.lastIndexOf('.');
            return lastDot > 0 ? owningClass.substring(0, lastDot) : "";
        }
        return "";
    }

    private String extractFilePath(CodeChunk chunk) {
        if (chunk.getClassMetadata() != null) {
            return chunk.getClassMetadata().getSourceFilePath();
        }
        return null;
    }

    private String buildNodeSummary(CodeChunk chunk) {
        StringBuilder summary = new StringBuilder();

        // Add type-specific summary
        if (chunk.getClassMetadata() != null) {
            var meta = chunk.getClassMetadata();
            if (meta.getClassSummary() != null) {
                summary.append(meta.getClassSummary()).append(" ");
            }
            if (!meta.getRoles().isEmpty()) {
                summary.append("Roles: ").append(String.join(", ", meta.getRoles())).append(" ");
            }
            if (!meta.getUsedLibraries().isEmpty()) {
                summary.append("Uses: ").append(String.join(", ", meta.getUsedLibraries()));
            }
        } else if (chunk.getMethodMetadata() != null) {
            var meta = chunk.getMethodMetadata();
            if (meta.getMethodSummary() != null && !meta.getMethodSummary().isEmpty()) {
                summary.append(meta.getMethodSummary());
            } else {
                summary.append("Method: ").append(meta.getMethodName());
            }
        }

        return summary.toString().trim();
    }

    private int extractLineCount(CodeChunk chunk) {
        if (chunk.getClassMetadata() != null) {
            return chunk.getClassMetadata().getLineCount();
        } else if (chunk.getMethodMetadata() != null) {
            return chunk.getMethodMetadata().getLineCount();
        }
        return 0;
    }
}
