package com.purchasingpower.autoflow.model.graph;

import com.purchasingpower.autoflow.model.ast.ChunkType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA Entity representing a node in the Code Knowledge Graph.
 * Includes knowledge graph fields for semantic domain analysis.
 */
@Entity
@Table(name = "CODE_NODES", indexes = {
        @Index(name = "idx_node_fqn", columnList = "fully_qualified_name"),
        @Index(name = "idx_node_repo", columnList = "repo_name"),
        @Index(name = "idx_node_domain", columnList = "domain"),
        @Index(name = "idx_node_capability", columnList = "business_capability")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphNode {

    @Id
    @Column(name = "node_id", length = 500)
    private String nodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false)
    private ChunkType type;

    @Column(name = "repo_name", nullable = false)
    private String repoName;

    @Column(name = "fully_qualified_name", nullable = false, length = 500)
    private String fullyQualifiedName;

    @Column(name = "simple_name", nullable = false)
    private String simpleName;

    @Column(name = "package_name")
    private String packageName;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(name = "parent_node_id", length = 500)
    private String parentNodeId;

    @Lob
    @Column(name = "summary")
    private String summary;

    @Column(name = "line_count")
    private int lineCount;

    @Column(name = "domain", length = 100)
    private String domain;

    @Column(name = "business_capability", length = 100)
    private String businessCapability;

    @Lob
    @Column(name = "features")
    private String features; // JSON array as string

    @Lob
    @Column(name = "concepts")
    private String concepts; // JSON array as string

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.lastUpdated = LocalDateTime.now();
    }
}