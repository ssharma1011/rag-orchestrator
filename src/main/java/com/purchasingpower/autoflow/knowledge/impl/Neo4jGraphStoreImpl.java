package com.purchasingpower.autoflow.knowledge.impl;

import com.purchasingpower.autoflow.core.CodeEntity;
import com.purchasingpower.autoflow.core.EntityType;
import com.purchasingpower.autoflow.core.Repository;
import com.purchasingpower.autoflow.core.impl.CodeEntityImpl;
import com.purchasingpower.autoflow.core.impl.RepositoryImpl;
import com.purchasingpower.autoflow.knowledge.GraphStore;
import com.purchasingpower.autoflow.knowledge.RelationshipDirection;
import com.purchasingpower.autoflow.model.java.JavaClass;
import com.purchasingpower.autoflow.model.java.JavaField;
import com.purchasingpower.autoflow.model.java.JavaMethod;
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
            // Repository indexes
            session.run("CREATE INDEX repo_id IF NOT EXISTS FOR (r:Repository) ON (r.id)");

            // OLD SCHEMA: Keep for backwards compatibility
            session.run("CREATE INDEX entity_id IF NOT EXISTS FOR (e:Entity) ON (e.id)");
            session.run("CREATE INDEX entity_repo IF NOT EXISTS FOR (e:Entity) ON (e.repositoryId)");
            session.run("CREATE INDEX entity_fqn IF NOT EXISTS FOR (e:Entity) ON (e.fullyQualifiedName)");

            // NEW SCHEMA: Type, Method, Field, Annotation indexes
            session.run("CREATE INDEX type_id IF NOT EXISTS FOR (t:Type) ON (t.id)");
            session.run("CREATE INDEX type_repo IF NOT EXISTS FOR (t:Type) ON (t.repositoryId)");
            session.run("CREATE INDEX type_fqn IF NOT EXISTS FOR (t:Type) ON (t.fqn)");
            session.run("CREATE INDEX type_name IF NOT EXISTS FOR (t:Type) ON (t.name)");

            session.run("CREATE INDEX method_id IF NOT EXISTS FOR (m:Method) ON (m.id)");
            session.run("CREATE INDEX method_repo IF NOT EXISTS FOR (m:Method) ON (m.repositoryId)");
            session.run("CREATE INDEX method_name IF NOT EXISTS FOR (m:Method) ON (m.name)");

            session.run("CREATE INDEX field_id IF NOT EXISTS FOR (f:Field) ON (f.id)");
            session.run("CREATE INDEX field_repo IF NOT EXISTS FOR (f:Field) ON (f.repositoryId)");

            session.run("CREATE INDEX annotation_id IF NOT EXISTS FOR (a:Annotation) ON (a.id)");
            session.run("CREATE INDEX annotation_fqn IF NOT EXISTS FOR (a:Annotation) ON (a.fqn)");
            session.run("CREATE INDEX annotation_repo IF NOT EXISTS FOR (a:Annotation) ON (a.repositoryId)");

            log.info("✅ Neo4j property indexes created");

            // VECTOR INDEXES for semantic search
            createVectorIndexes(session);

        } catch (Exception e) {
            log.warn("⚠️  Failed to create indexes: {}", e.getMessage());
        }
    }

    private void createVectorIndexes(Session session) {
        try {
            // Create vector index for Type embeddings (1024 dimensions for mxbai-embed-large)
            String typeVectorIndex = """
                CREATE VECTOR INDEX type_embedding_index IF NOT EXISTS
                FOR (t:Type) ON (t.embedding)
                OPTIONS {indexConfig: {
                  `vector.dimensions`: 1024,
                  `vector.similarity_function`: 'cosine'
                }}
                """;
            session.run(typeVectorIndex);
            log.info("✅ Created vector index: type_embedding_index");

            // Create vector index for Method embeddings
            String methodVectorIndex = """
                CREATE VECTOR INDEX method_embedding_index IF NOT EXISTS
                FOR (m:Method) ON (m.embedding)
                OPTIONS {indexConfig: {
                  `vector.dimensions`: 1024,
                  `vector.similarity_function`: 'cosine'
                }}
                """;
            session.run(methodVectorIndex);
            log.info("✅ Created vector index: method_embedding_index");

        } catch (Exception e) {
            log.warn("⚠️  Failed to create vector indexes (may require Neo4j 5.x+): {}", e.getMessage());
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
        log.info("📊 [GRAPH DB REQUEST] Executing Cypher query");
        log.debug("📊 [GRAPH DB REQUEST] Query: {}", cypher);
        log.debug("📊 [GRAPH DB REQUEST] Parameters: {}", parameters);

        long startTime = System.currentTimeMillis();
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

                long duration = System.currentTimeMillis() - startTime;
                log.info("📊 [GRAPH DB RESPONSE] Query completed in {}ms, Returned {} entities", duration, entities.size());
                log.debug("📊 [GRAPH DB RESPONSE] Entities: {}", entities.stream()
                    .map(CodeEntity::getFullyQualifiedName)
                    .limit(10)
                    .toList());

                return entities;
            });
        }
    }

    @Override
    public List<Map<String, Object>> executeCypherQueryRaw(String cypher, Map<String, Object> parameters) {
        log.info("📊 [GRAPH DB REQUEST RAW] Executing raw Cypher query");
        log.debug("📊 [GRAPH DB REQUEST RAW] Query: {}", cypher);
        log.debug("📊 [GRAPH DB REQUEST RAW] Parameters: {}", parameters);

        long startTime = System.currentTimeMillis();
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(cypher, parameters != null ? parameters : Map.of());
                List<Map<String, Object>> results = new ArrayList<>();
                while (result.hasNext()) {
                    results.add(result.next().asMap());
                }

                long duration = System.currentTimeMillis() - startTime;
                log.info("📊 [GRAPH DB RESPONSE RAW] Query completed in {}ms, Returned {} rows", duration, results.size());
                if (!results.isEmpty()) {
                    log.debug("📊 [GRAPH DB RESPONSE RAW] First row keys: {}", results.get(0).keySet());
                }

                return results;
            });
        }
    }

    @Override
    public int executeCypherWrite(String cypher, Map<String, Object> parameters) {
        log.info("✍️  [GRAPH DB WRITE] Executing write Cypher query");
        log.debug("✍️  [GRAPH DB WRITE] Query: {}", cypher);
        log.debug("✍️  [GRAPH DB WRITE] Parameters: {}", parameters);

        long startTime = System.currentTimeMillis();
        try (Session session = driver.session()) {
            return session.executeWrite(tx -> {
                Result result = tx.run(cypher, parameters != null ? parameters : Map.of());
                int nodesAffected = result.consume().counters().nodesDeleted() +
                                  result.consume().counters().nodesCreated();

                long duration = System.currentTimeMillis() - startTime;
                log.info("✍️  [GRAPH DB WRITE] Write completed in {}ms, {} nodes affected",
                    duration, nodesAffected);

                return nodesAffected;
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
            .fullyQualifiedName(getStringValue(node, "fqn"))  // FIX: Changed from "fullyQualifiedName" to "fqn"
            .filePath(getStringValue(node, "filePath"))
            .startLine(getIntValue(node, "startLine"))
            .endLine(getIntValue(node, "endLine"))
            .sourceCode(getStringValue(node, "sourceCode"))
            .summary(getStringValue(node, "summary"))
            .annotations(getListValue(node, "annotations"))
            .build();
    }

    private String getStringValue(Node node, String key) {
        // FIX: Return null instead of empty string to properly handle CONTAINS queries
        return node.containsKey(key) && !node.get(key).isNull() ? node.get(key).asString() : null;
    }

    private int getIntValue(Node node, String key) {
        return node.containsKey(key) && !node.get(key).isNull() ? node.get(key).asInt() : 0;
    }

    private List<String> getListValue(Node node, String key) {
        if (node.containsKey(key) && !node.get(key).isNull()) {
            return node.get(key).asList(org.neo4j.driver.Value::asString);
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

    // ========== NEW SCHEMA IMPLEMENTATION ==========

    @Override
    public void storeJavaClass(JavaClass javaClass) {
        log.info("📦 Storing JavaClass: {}", javaClass.getFullyQualifiedName());

        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                storeTypeNode(tx, javaClass);
                storeMethods(tx, javaClass);
                storeFields(tx, javaClass);
                storeAnnotationsForClass(tx, javaClass);
                return null;
            });

            log.debug("✅ Stored class {} with {} methods, {} fields",
                javaClass.getFullyQualifiedName(),
                javaClass.getMethods().size(),
                javaClass.getFields().size());
        }
    }

    @Override
    public void storeJavaClasses(List<JavaClass> javaClasses) {
        log.info("📂 Storing {} Java classes", javaClasses.size());

        for (JavaClass javaClass : javaClasses) {
            try {
                storeJavaClass(javaClass);
            } catch (Exception e) {
                log.error("❌ Failed to store {}: {}",
                    javaClass.getFullyQualifiedName(), e.getMessage());
            }
        }

        log.info("✅ Completed storing {} classes", javaClasses.size());
    }

    private void storeTypeNode(org.neo4j.driver.TransactionContext tx, JavaClass javaClass) {
        String cypher = """
            MERGE (t:Type {id: $id})
            SET t.repositoryId = $repositoryId,
                t.name = $name,
                t.packageName = $packageName,
                t.fqn = $fqn,
                t.filePath = $filePath,
                t.startLine = $startLine,
                t.endLine = $endLine,
                t.kind = $kind,
                t.extendsClass = $extendsClass,
                t.implementsInterfaces = $implementsInterfaces,
                t.description = $description,
                t.embedding = $embedding
            """;

        tx.run(cypher, createParams(
            "id", javaClass.getId(),
            "repositoryId", javaClass.getRepositoryId(),
            "name", javaClass.getName(),
            "packageName", javaClass.getPackageName(),
            "fqn", javaClass.getFullyQualifiedName(),
            "filePath", javaClass.getFilePath(),
            "startLine", javaClass.getStartLine(),
            "endLine", javaClass.getEndLine(),
            "kind", javaClass.getKind().name(),
            "extendsClass", javaClass.getExtendsClass(),
            "implementsInterfaces", javaClass.getImplementsInterfaces(),
            "description", javaClass.getDescription(),
            "embedding", javaClass.getEmbedding() != null ? javaClass.getEmbedding() : List.of()
        ));
    }

    private void storeMethods(org.neo4j.driver.TransactionContext tx, JavaClass javaClass) {
        for (JavaMethod method : javaClass.getMethods()) {
            storeMethodNode(tx, method, javaClass);
            storeMethodRelationships(tx, method, javaClass);
            storeAnnotationsForMethod(tx, method, javaClass);
        }
    }

    private void storeMethodNode(org.neo4j.driver.TransactionContext tx,
                                   JavaMethod method, JavaClass javaClass) {
        String cypher = """
            MERGE (m:Method {id: $id})
            SET m.repositoryId = $repositoryId,
                m.name = $name,
                m.signature = $signature,
                m.returnType = $returnType,
                m.startLine = $startLine,
                m.endLine = $endLine,
                m.description = $description,
                m.embedding = $embedding
            """;

        tx.run(cypher, createParams(
            "id", method.getId(),
            "repositoryId", javaClass.getRepositoryId(),
            "name", method.getName(),
            "signature", method.getSignature(),
            "returnType", method.getReturnType(),
            "startLine", method.getStartLine(),
            "endLine", method.getEndLine(),
            "description", method.getDescription(),
            "embedding", method.getEmbedding() != null ? method.getEmbedding() : List.of()
        ));

        // Create DECLARES relationship
        String declaresRel = """
            MATCH (t:Type {id: $classId})
            MATCH (m:Method {id: $methodId})
            MERGE (t)-[:DECLARES]->(m)
            """;
        tx.run(declaresRel, Map.of("classId", javaClass.getId(), "methodId", method.getId()));
    }

    private void storeMethodRelationships(org.neo4j.driver.TransactionContext tx,
                                           JavaMethod method, JavaClass javaClass) {
        // Store CALLS relationships for method calls
        for (String calledMethodName : method.getMethodCalls()) {
            String callsRel = """
                MATCH (m:Method {id: $methodId})
                MERGE (m)-[:CALLS {methodName: $calledMethodName}]->(m)
                """;
            tx.run(callsRel, Map.of(
                "methodId", method.getId(),
                "calledMethodName", calledMethodName
            ));
        }
    }

    private void storeFields(org.neo4j.driver.TransactionContext tx, JavaClass javaClass) {
        for (JavaField field : javaClass.getFields()) {
            storeFieldNode(tx, field, javaClass);
            storeAnnotationsForField(tx, field, javaClass);
        }
    }

    private void storeFieldNode(org.neo4j.driver.TransactionContext tx,
                                 JavaField field, JavaClass javaClass) {
        String cypher = """
            MERGE (f:Field {id: $id})
            SET f.repositoryId = $repositoryId,
                f.name = $name,
                f.type = $type,
                f.lineNumber = $lineNumber
            """;

        tx.run(cypher, createParams(
            "id", field.getId(),
            "repositoryId", javaClass.getRepositoryId(),
            "name", field.getName(),
            "type", field.getType(),
            "lineNumber", field.getLineNumber()
        ));

        // Create DECLARES relationship
        String declaresRel = """
            MATCH (t:Type {id: $classId})
            MATCH (f:Field {id: $fieldId})
            MERGE (t)-[:DECLARES]->(f)
            """;
        tx.run(declaresRel, Map.of("classId", javaClass.getId(), "fieldId", field.getId()));
    }

    private void storeAnnotationsForClass(org.neo4j.driver.TransactionContext tx,
                                           JavaClass javaClass) {
        for (String annotation : javaClass.getAnnotations()) {
            storeAnnotation(tx, annotation, javaClass.getRepositoryId());
            linkAnnotationToType(tx, annotation, javaClass.getId(), javaClass.getRepositoryId());
        }
    }

    private void storeAnnotationsForMethod(org.neo4j.driver.TransactionContext tx,
                                            JavaMethod method, JavaClass javaClass) {
        for (String annotation : method.getAnnotations()) {
            storeAnnotation(tx, annotation, javaClass.getRepositoryId());
            linkAnnotationToMethod(tx, annotation, method.getId(), javaClass.getRepositoryId());
        }
    }

    private void storeAnnotationsForField(org.neo4j.driver.TransactionContext tx,
                                           JavaField field, JavaClass javaClass) {
        for (String annotation : field.getAnnotations()) {
            storeAnnotation(tx, annotation, javaClass.getRepositoryId());
            linkAnnotationToField(tx, annotation, field.getId(), javaClass.getRepositoryId());
        }
    }

    private void storeAnnotation(org.neo4j.driver.TransactionContext tx,
                                  String annotation, String repositoryId) {
        String cypher = """
            MERGE (a:Annotation {fqn: $fqn, repositoryId: $repositoryId})
            ON CREATE SET a.id = randomUUID()
            """;
        tx.run(cypher, Map.of("fqn", annotation, "repositoryId", repositoryId));
    }

    private void linkAnnotationToType(org.neo4j.driver.TransactionContext tx,
                                       String annotation, String typeId, String repositoryId) {
        String cypher = """
            MATCH (t:Type {id: $typeId})
            MATCH (a:Annotation {fqn: $fqn, repositoryId: $repositoryId})
            MERGE (t)-[:ANNOTATED_BY]->(a)
            """;
        tx.run(cypher, Map.of("typeId", typeId, "fqn", annotation, "repositoryId", repositoryId));
    }

    private void linkAnnotationToMethod(org.neo4j.driver.TransactionContext tx,
                                         String annotation, String methodId, String repositoryId) {
        String cypher = """
            MATCH (m:Method {id: $methodId})
            MATCH (a:Annotation {fqn: $fqn, repositoryId: $repositoryId})
            MERGE (m)-[:ANNOTATED_BY]->(a)
            """;
        tx.run(cypher, Map.of("methodId", methodId, "fqn", annotation, "repositoryId", repositoryId));
    }

    private void linkAnnotationToField(org.neo4j.driver.TransactionContext tx,
                                        String annotation, String fieldId, String repositoryId) {
        String cypher = """
            MATCH (f:Field {id: $fieldId})
            MATCH (a:Annotation {fqn: $fqn, repositoryId: $repositoryId})
            MERGE (f)-[:ANNOTATED_BY]->(a)
            """;
        tx.run(cypher, Map.of("fieldId", fieldId, "fqn", annotation, "repositoryId", repositoryId));
    }
}
