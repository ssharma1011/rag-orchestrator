package com.purchasingpower.autoflow.knowledge;

import com.purchasingpower.autoflow.core.CodeEntity;
import com.purchasingpower.autoflow.core.EntityType;
import com.purchasingpower.autoflow.core.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for graph database operations.
 *
 * @since 2.0.0
 */
public interface GraphStore {

    void storeRepository(Repository repository);

    Optional<Repository> getRepository(String repositoryId);

    List<Repository> listRepositories();

    void deleteRepository(String repositoryId);

    void storeEntity(CodeEntity entity);

    void storeEntities(List<CodeEntity> entities);

    Optional<CodeEntity> getEntity(String entityId);

    List<CodeEntity> findEntitiesByType(String repositoryId, EntityType entityType);

    void storeRelationship(String fromId, String toId, String relationshipType, Map<String, Object> properties);

    List<CodeEntity> findRelatedEntities(String entityId, String relationshipType, RelationshipDirection direction);

    List<CodeEntity> executeCypherQuery(String cypher, Map<String, Object> parameters);

    List<Map<String, Object>> executeCypherQueryRaw(String cypher, Map<String, Object> parameters);
}
