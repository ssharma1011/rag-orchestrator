package com.purchasingpower.autoflow.knowledge;

import com.purchasingpower.autoflow.core.CodeEntity;
import com.purchasingpower.autoflow.core.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for graph database operations.
 *
 * <p>Abstracts Neo4j operations for storing and querying the code knowledge graph.
 * Implementations handle entity storage, relationship management, and structural queries.
 *
 * @since 2.0.0
 */
public interface GraphStore {

    // =========================================================================
    // Repository Operations
    // =========================================================================

    /**
     * Store or update a repository.
     *
     * @param repository Repository to store
     */
    void storeRepository(Repository repository);

    /**
     * Get a repository by ID.
     *
     * @param repositoryId Repository ID
     * @return Repository if found
     */
    Optional<Repository> getRepository(String repositoryId);

    /**
     * List all indexed repositories.
     *
     * @return List of all repositories
     */
    List<Repository> listRepositories();

    /**
     * Delete a repository and all its entities.
     *
     * @param repositoryId Repository ID to delete
     */
    void deleteRepository(String repositoryId);

    // =========================================================================
    // Entity Operations
    // =========================================================================

    /**
     * Store a code entity.
     *
     * @param entity Entity to store
     */
    void storeEntity(CodeEntity entity);

    /**
     * Store multiple entities in batch.
     *
     * @param entities Entities to store
     */
    void storeEntities(List<CodeEntity> entities);

    /**
     * Get an entity by ID.
     *
     * @param entityId Entity ID
     * @return Entity if found
     */
    Optional<CodeEntity> getEntity(String entityId);

    /**
     * Find entities by type in a repository.
     *
     * @param repositoryId Repository ID
     * @param entityType Entity type filter
     * @return Matching entities
     */
    List<CodeEntity> findEntitiesByType(String repositoryId, CodeEntity.EntityType entityType);

    // =========================================================================
    // Relationship Operations
    // =========================================================================

    /**
     * Store a relationship between entities.
     *
     * @param fromId Source entity ID
     * @param toId Target entity ID
     * @param relationshipType Type of relationship
     * @param properties Additional properties
     */
    void storeRelationship(String fromId, String toId, String relationshipType, Map<String, Object> properties);

    /**
     * Find entities related to a given entity.
     *
     * @param entityId Entity ID
     * @param relationshipType Relationship type (or null for all)
     * @param direction Direction (INCOMING, OUTGOING, BOTH)
     * @return Related entities
     */
    List<CodeEntity> findRelatedEntities(String entityId, String relationshipType, RelationshipDirection direction);

    /**
     * Relationship traversal direction.
     */
    enum RelationshipDirection {
        INCOMING,
        OUTGOING,
        BOTH
    }

    // =========================================================================
    // Query Operations
    // =========================================================================

    /**
     * Execute a Cypher query and return entities.
     *
     * @param cypher Cypher query
     * @param parameters Query parameters
     * @return Matching entities
     */
    List<CodeEntity> executeCypherQuery(String cypher, Map<String, Object> parameters);

    /**
     * Execute a Cypher query and return raw results.
     *
     * @param cypher Cypher query
     * @param parameters Query parameters
     * @return List of result maps
     */
    List<Map<String, Object>> executeCypherQueryRaw(String cypher, Map<String, Object> parameters);
}
