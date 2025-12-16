package com.purchasingpower.autoflow.model.graph;

import com.purchasingpower.autoflow.model.ast.DependencyEdge;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA Entity representing a directional relationship between two code nodes.
 * Maps to an Oracle table 'CODE_EDGES'.
 */
@Entity
@Table(name = "CODE_EDGES", indexes = {
        @Index(name = "idx_edge_source", columnList = "source_node_id"),
        @Index(name = "idx_edge_target", columnList = "target_node_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_node_id", nullable = false, length = 500)
    private String sourceNodeId;

    @Column(name = "target_node_id", nullable = false, length = 500)
    private String targetNodeId; // This might be a FQN if the node doesn't exist yet

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false)
    private DependencyEdge.RelationshipType relationshipType;

    @Enumerated(EnumType.STRING)
    @Column(name = "cardinality")
    private DependencyEdge.Cardinality cardinality;

    @Column(name = "context_info")
    private String context; // e.g. field name, method name

    @Column(name = "repo_name", nullable = false)
    private String repoName;
}