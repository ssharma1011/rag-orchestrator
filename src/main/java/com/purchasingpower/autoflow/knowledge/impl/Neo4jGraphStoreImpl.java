package com.purchasingpower.autoflow.knowledge.impl;

import com.purchasingpower.autoflow.core.CodeEntity;
import com.purchasingpower.autoflow.core.EntityType;
import com.purchasingpower.autoflow.core.Repository;
import com.purchasingpower.autoflow.core.impl.CodeEntityImpl;
import com.purchasingpower.autoflow.core.impl.RepositoryImpl;
import com.purchasingpower.autoflow.knowledge.GraphStore;
import com.purchasingpower.autoflow.knowledge.RelationshipDirection;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.types.Node;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Neo4j implementation of GraphStore interface.
 *
 * @since 2.0.0
 */
@Slf4j
@Service
public class Neo4jGraphStoreImpl implements GraphStore {

    @Value("${neo4j.uri:bolt://localhost:7687}")
    private String neo4jUri;

    @Value("${neo4j.username:neo4j}")
    private String neo4jUsername;

    @Value("${neo4j.password:password}")
    private String neo4jPassword;

    private Driver driver;

    @PostConstruct
    public void init() {
        log.info("Initializing Neo4j GraphStore at: {}", neo4jUri);
        driver = GraphDatabase.driver(neo4jUri,
                AuthTokens.basic(neo4jUsername, neo4jPassword));
        createIndexes();
    }

    @PreDestroy
    public void close() {
        if (driver != null) {
            driver.close();
            log.info("Neo4j GraphStore connection closed");
        }
    }

    private void createIndexes() {
        try (Session session = driver.session()) {
            session.run("CREATE INDEX repo_id IF NOT EXISTS FOR (r:Repository) ON (r.id)");
            session.run("CREATE INDEX entity_id IF NOT EXISTS FOR (e:Entity) ON (e.id)");
            session.run("CREATE INDEX entity_repo IF NOT EXISTS FOR (e:Entity) ON (e.repositoryId)");
            session.run("CREATE INDEX entity_fqn IF NOT EXISTS FOR (e:Entity) ON (e.fullyQualifiedName)");
            log.info("Neo4j indexes created");
        } catch (Exception e) {
            log.warn("Failed to create indexes: {}", e.getMessage());
        }
    }

    @Override
    public void storeRepository(Repository repository) {
        String cypher = """
            MERGE (r:Repository {id: $id})
            SET r.url = $url,
                r.branch = $branch,
                r.type = $type,
                r.language = $language,
                r.domain = $domain,
                r.lastIndexedAt = $lastIndexedAt,
                r.lastIndexedCommit = $lastIndexedCommit
            """;

        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run(cypher, createParams(
                    "id", repository.getId(),
                    "url", repository.getUrl(),
                    "branch", repository.getBranch(),
                    "type", repository.getType() != null ? repository.getType().name() : null,
                    "language", repository.getLanguage(),
                    "domain", repository.getDomain(),
                    "lastIndexedAt", repository.getLastIndexedAt() != null ? repository.getLastIndexedAt().toString() : null,
                    "lastIndexedCommit", repository.getLastIndexedCommit()
                ));
                return null;
            });
        }
    }

    @Override
    public Optional<Repository> getRepository(String repositoryId) {
        String cypher = "MATCH (r:Repository {id: $id}) RETURN r";

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(cypher, Collections.singletonMap("id", repositoryId));
                if (result.hasNext()) {
                    return Optional.of(nodeToRepository(result.single().get("r").asNode()));
                }
                return Optional.empty();
            });
        }
    }

    @Override
    public List<Repository> listRepositories() {
        String cypher = "MATCH (r:Repository) RETURN r";

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(cypher);
                List<Repository> repos = new ArrayList<>();
                while (result.hasNext()) {
                    repos.add(nodeToRepository(result.next().get("r").asNode()));
                }
                return repos;
            });
        }
    }

    @Override
    public void deleteRepository(String repositoryId) {
        String cypher = """
            MATCH (r:Repository {id: $id})
            OPTIONAL MATCH (e:Entity {repositoryId: $id})
            DETACH DELETE r, e
            """;

        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run(cypher, Collections.singletonMap("id", repositoryId));
                return null;
            });
        }
    }

    @Override
    public void storeEntity(CodeEntity entity) {
        storeEntities(List.of(entity));
    }

    @Override
    public void storeEntities(List<CodeEntity> entities) {
        String cypher = """
            MERGE (e:Entity {id: $id})
            SET e.type = $type,
                e.repositoryId = $repositoryId,
                e.name = $name,
                e.fullyQualifiedName = $fullyQualifiedName,
                e.filePath = $filePath,
                e.startLine = $startLine,
                e.endLine = $endLine,
                e.sourceCode = $sourceCode,
                e.summary = $summary,
                e.annotations = $annotations
            """;

        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                for (CodeEntity entity : entities) {
                    tx.run(cypher, createParams(
                        "id", entity.getId(),
                        "type", entity.getType() != null ? entity.getType().name() : null,
                        "repositoryId", entity.getRepositoryId(),
                        "name", entity.getName(),
                        "fullyQualifiedName", entity.getFullyQualifiedName(),
                        "filePath", entity.getFilePath(),
                        "startLine", entity.getStartLine(),
                        "endLine", entity.getEndLine(),
                        "sourceCode", entity.getSourceCode(),
                        "summary", entity.getSummary(),
                        "annotations", entity.getAnnotations() != null ? entity.getAnnotations() : Collections.emptyList()
                    ));
                }
                return null;
            });
        }
    }

    @Override
    public Optional<CodeEntity> getEntity(String entityId) {
        String cypher = "MATCH (e:Entity {id: $id}) RETURN e";

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(cypher, Collections.singletonMap("id", entityId));
                if (result.hasNext()) {
                    return Optional.of(nodeToCodeEntity(result.single().get("e").asNode()));
                }
                return Optional.empty();
            });
        }
    }

    @Override
    public List<CodeEntity> findEntitiesByType(String repositoryId, EntityType entityType) {
        String cypher = """
            MATCH (e:Entity {repositoryId: $repositoryId, type: $type})
            RETURN e
            """;

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(cypher, Map.of(
                    "repositoryId", repositoryId,
                    "type", entityType.name()
                ));
                List<CodeEntity> entities = new ArrayList<>();
                while (result.hasNext()) {
                    entities.add(nodeToCodeEntity(result.next().get("e").asNode()));
                }
                return entities;
            });
        }
    }

    @Override
    public void storeRelationship(String fromId, String toId, String relationshipType, Map<String, Object> properties) {
        String cypher = String.format("""
            MATCH (from:Entity {id: $fromId})
            MATCH (to:Entity {id: $toId})
            MERGE (from)-[r:%s]->(to)
            SET r += $properties
            """, relationshipType);

        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run(cypher, Map.of(
                    "fromId", fromId,
                    "toId", toId,
                    "properties", properties != null ? properties : Map.of()
                ));
                return null;
            });
        }
    }

    @Override
    public List<CodeEntity> findRelatedEntities(String entityId, String relationshipType, RelationshipDirection direction) {
        String pattern = switch (direction) {
            case INCOMING -> "<-[r%s]-";
            case OUTGOING -> "-[r%s]->";
            case BOTH -> "-[r%s]-";
        };

        String relFilter = relationshipType != null ? ":" + relationshipType : "";
        String formattedPattern = String.format(pattern, relFilter);

        String cypher = String.format("""
            MATCH (e:Entity {id: $entityId})%s(related:Entity)
            RETURN related
            """, formattedPattern);

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(cypher, Collections.singletonMap("entityId", entityId));
                List<CodeEntity> entities = new ArrayList<>();
                while (result.hasNext()) {
                    entities.add(nodeToCodeEntity(result.next().get("related").asNode()));
                }
                return entities;
            });
        }
    }

    @Override
    public List<CodeEntity> executeCypherQuery(String cypher, Map<String, Object> parameters) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(cypher, parameters != null ? parameters : Map.of());
                List<CodeEntity> entities = new ArrayList<>();
                while (result.hasNext()) {
                    var record = result.next();
                    if (record.containsKey("e")) {
                        entities.add(nodeToCodeEntity(record.get("e").asNode()));
                    }
                }
                return entities;
            });
        }
    }

    @Override
    public List<Map<String, Object>> executeCypherQueryRaw(String cypher, Map<String, Object> parameters) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(cypher, parameters != null ? parameters : Map.of());
                List<Map<String, Object>> results = new ArrayList<>();
                while (result.hasNext()) {
                    results.add(result.next().asMap());
                }
                return results;
            });
        }
    }

    private Map<String, Object> createParams(Object... keyValues) {
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = (String) keyValues[i];
            Object value = keyValues[i + 1];
            params.put(key, value != null ? value : "");
        }
        return params;
    }

    private Repository nodeToRepository(Node node) {
        return RepositoryImpl.builder()
            .id(getStringValue(node, "id"))
            .url(getStringValue(node, "url"))
            .branch(getStringValue(node, "branch"))
            .type(parseRepositoryType(getStringValue(node, "type")))
            .language(getStringValue(node, "language"))
            .domain(getStringValue(node, "domain"))
            .lastIndexedAt(parseLocalDateTime(getStringValue(node, "lastIndexedAt")))
            .lastIndexedCommit(getStringValue(node, "lastIndexedCommit"))
            .build();
    }

    private CodeEntity nodeToCodeEntity(Node node) {
        return CodeEntityImpl.builder()
            .id(getStringValue(node, "id"))
            .type(parseEntityType(getStringValue(node, "type")))
            .repositoryId(getStringValue(node, "repositoryId"))
            .name(getStringValue(node, "name"))
            .fullyQualifiedName(getStringValue(node, "fullyQualifiedName"))
            .filePath(getStringValue(node, "filePath"))
            .startLine(getIntValue(node, "startLine"))
            .endLine(getIntValue(node, "endLine"))
            .sourceCode(getStringValue(node, "sourceCode"))
            .summary(getStringValue(node, "summary"))
            .annotations(getListValue(node, "annotations"))
            .build();
    }

    private String getStringValue(Node node, String key) {
        return node.containsKey(key) && !node.get(key).isNull() ? node.get(key).asString() : "";
    }

    private int getIntValue(Node node, String key) {
        return node.containsKey(key) && !node.get(key).isNull() ? node.get(key).asInt() : 0;
    }

    private List<String> getListValue(Node node, String key) {
        if (node.containsKey(key) && !node.get(key).isNull()) {
            return node.get(key).asList(v -> v.asString());
        }
        return new ArrayList<>();
    }

    private EntityType parseEntityType(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return EntityType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private com.purchasingpower.autoflow.core.RepositoryType parseRepositoryType(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return com.purchasingpower.autoflow.core.RepositoryType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private LocalDateTime parseLocalDateTime(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
