package com.purchasingpower.autoflow.storage;

import com.purchasingpower.autoflow.model.neo4j.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.neo4j.driver.types.Node;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Neo4j Knowledge Graph Store for code entities and relationships.
 *
 * CRITICAL FIX: Added null-safe parameter handling to fix Map.ofEntries() NPE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jGraphStore {

    @Value("${neo4j.uri:bolt://localhost:7687}")
    private String neo4jUri;

    @Value("${neo4j.username:neo4j}")
    private String neo4jUsername;

    @Value("${neo4j.password:password}")
    private String neo4jPassword;

    private Driver driver;

    // ================================================================
    // LIFECYCLE MANAGEMENT
    // ================================================================

    @PostConstruct
    public void init() {
        log.info("Connecting to Neo4j at: {}", neo4jUri);
        driver = GraphDatabase.driver(neo4jUri,
                AuthTokens.basic(neo4jUsername, neo4jPassword));
        createIndexes();
    }

    @PreDestroy
    public void close() {
        if (driver != null) {
            driver.close();
            log.info("Neo4j connection closed");
        }
    }

    private void createIndexes() {
        try (Session session = driver.session()) {
            session.run("CREATE INDEX class_fqn IF NOT EXISTS FOR (c:Class) ON (c.fullyQualifiedName)");
            session.run("CREATE INDEX method_fqn IF NOT EXISTS FOR (m:Method) ON (m.fullyQualifiedName)");
            session.run("CREATE INDEX field_fqn IF NOT EXISTS FOR (f:Field) ON (f.fullyQualifiedName)");
            session.run("CREATE INDEX entity_id IF NOT EXISTS FOR (e:Class) ON (e.id)");
            session.run("CREATE INDEX method_id IF NOT EXISTS FOR (m:Method) ON (m.id)");
            session.run("CREATE INDEX field_id IF NOT EXISTS FOR (f:Field) ON (f.id)");
            log.info("Neo4j indexes created");
        } catch (Exception e) {
            log.warn("Failed to create indexes (may already exist): {}", e.getMessage());
        }
    }

    // ================================================================
    // CRITICAL FIX: NULL-SAFE PARAMETER CREATION
    // ================================================================

    /**
     * Helper method to create params map without nulls.
     * Map.ofEntries() throws NPE on null values, this method handles it gracefully.
     */
    private Map<String, Object> createParams(Object... keyValues) {
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = (String) keyValues[i];
            Object value = keyValues[i + 1];

            if (value == null) {
                value = "";  // Neo4j handles empty strings better than nulls
            }

            params.put(key, value);
        }
        return params;
    }

    // ================================================================
    // STORE METHODS
    // ================================================================

    public void storeCodeGraph(ParsedCodeGraph graph) {
        var callCtx = com.purchasingpower.autoflow.util.ExternalCallLogger.startCall(
                com.purchasingpower.autoflow.model.ServiceType.NEO4J,
                "StoreCodeGraph",
                log
        );

        callCtx.logRequest("Storing code graph",
                "Classes", graph.getClasses().size(),
                "Methods", graph.getMethods().size(),
                "Fields", graph.getFields().size(),
                "Relationships", graph.getTotalRelationships());

        try (Session session = driver.session()) {
            // ✅ FIX: Use executeWrite instead of deprecated writeTransaction
            session.executeWrite(tx -> {
                for (ClassNode classNode : graph.getClasses()) {
                    storeClassNode(tx, classNode);
                }
                for (MethodNode methodNode : graph.getMethods()) {
                    storeMethodNode(tx, methodNode);
                }
                for (FieldNode fieldNode : graph.getFields()) {
                    storeFieldNode(tx, fieldNode);
                }
                for (CodeRelationship rel : graph.getRelationships()) {
                    storeRelationship(tx, rel);
                }
                return null;
            });

            callCtx.logResponse("Code graph stored successfully");

        } catch (Exception e) {
            callCtx.logError("Failed to store code graph", e);
            throw new RuntimeException("Failed to store code graph", e);
        }
    }

    // ✅ FIX: Changed from Transaction to TransactionContext for new Neo4j API
    private void storeClassNode(org.neo4j.driver.TransactionContext tx, ClassNode classNode) {
        String cypher = """
            MERGE (c:Class {id: $id})
            SET c.name = $name,
                c.fullyQualifiedName = $fqn,
                c.packageName = $packageName,
                c.sourceFilePath = $sourceFilePath,
                c.startLine = $startLine,
                c.endLine = $endLine,
                c.classType = $classType,
                c.superClassName = $superClassName,
                c.interfaces = $interfaces,
                c.accessModifier = $accessModifier,
                c.isAbstract = $isAbstract,
                c.isFinal = $isFinal,
                c.isStatic = $isStatic,
                c.annotations = $annotations,
                c.sourceCode = $sourceCode,
                c.javadoc = $javadoc
            """;

        // CRITICAL FIX: Use createParams() instead of Map.ofEntries()
        Map<String, Object> params = createParams(
                "id", classNode.getId(),
                "name", classNode.getName(),
                "fqn", classNode.getFullyQualifiedName(),
                "packageName", classNode.getPackageName(),
                "sourceFilePath", classNode.getSourceFilePath(),
                "startLine", classNode.getStartLine(),
                "endLine", classNode.getEndLine(),
                "classType", classNode.getClassType() != null ? classNode.getClassType().name() : "CLASS",
                "superClassName", classNode.getSuperClassName(),
                "interfaces", classNode.getInterfaces() != null ? classNode.getInterfaces() : Collections.emptyList(),
                "accessModifier", classNode.getAccessModifier(),
                "isAbstract", classNode.isAbstract(),
                "isFinal", classNode.isFinal(),
                "isStatic", classNode.isStatic(),
                "annotations", classNode.getAnnotations() != null ? classNode.getAnnotations() : Collections.emptyList(),
                "sourceCode", classNode.getSourceCode(),
                "javadoc", classNode.getJavadoc()
        );

        tx.run(cypher, params);
    }

    private void storeMethodNode(org.neo4j.driver.TransactionContext tx, MethodNode methodNode) {
        String cypher = """
            MERGE (m:Method {id: $id})
            SET m.name = $name,
                m.fullyQualifiedName = $fqn,
                m.className = $className,
                m.sourceFilePath = $sourceFilePath,
                m.startLine = $startLine,
                m.endLine = $endLine,
                m.returnType = $returnType,
                m.accessModifier = $accessModifier,
                m.isStatic = $isStatic,
                m.isFinal = $isFinal,
                m.isAbstract = $isAbstract,
                m.isSynchronized = $isSynchronized,
                m.isConstructor = $isConstructor,
                m.annotations = $annotations,
                m.sourceCode = $sourceCode,
                m.javadoc = $javadoc,
                m.thrownExceptions = $thrownExceptions
            """;

        Map<String, Object> params = createParams(
                "id", methodNode.getId(),
                "name", methodNode.getName(),
                "fqn", methodNode.getFullyQualifiedName(),
                "className", methodNode.getClassName(),
                "sourceFilePath", methodNode.getSourceFilePath(),
                "startLine", methodNode.getStartLine(),
                "endLine", methodNode.getEndLine(),
                "returnType", methodNode.getReturnType(),
                "accessModifier", methodNode.getAccessModifier(),
                "isStatic", methodNode.isStatic(),
                "isFinal", methodNode.isFinal(),
                "isAbstract", methodNode.isAbstract(),
                "isSynchronized", methodNode.isSynchronized(),
                "isConstructor", methodNode.isConstructor(),
                "annotations", methodNode.getAnnotations() != null ? methodNode.getAnnotations() : Collections.emptyList(),
                "sourceCode", methodNode.getSourceCode(),
                "javadoc", methodNode.getJavadoc(),
                "thrownExceptions", methodNode.getThrownExceptions() != null ? methodNode.getThrownExceptions() : Collections.emptyList()
        );

        tx.run(cypher, params);
    }

    private void storeFieldNode(org.neo4j.driver.TransactionContext tx, FieldNode fieldNode) {
        String cypher = """
            MERGE (f:Field {id: $id})
            SET f.name = $name,
                f.fullyQualifiedName = $fqn,
                f.className = $className,
                f.sourceFilePath = $sourceFilePath,
                f.lineNumber = $lineNumber,
                f.type = $type,
                f.accessModifier = $accessModifier,
                f.isStatic = $isStatic,
                f.isFinal = $isFinal,
                f.isTransient = $isTransient,
                f.isVolatile = $isVolatile,
                f.annotations = $annotations,
                f.initialValue = $initialValue,
                f.javadoc = $javadoc
            """;

        Map<String, Object> params = createParams(
                "id", fieldNode.getId(),
                "name", fieldNode.getName(),
                "fqn", fieldNode.getFullyQualifiedName(),
                "className", fieldNode.getClassName(),
                "sourceFilePath", fieldNode.getSourceFilePath(),
                "lineNumber", fieldNode.getLineNumber(),
                "type", fieldNode.getType(),
                "accessModifier", fieldNode.getAccessModifier(),
                "isStatic", fieldNode.isStatic(),
                "isFinal", fieldNode.isFinal(),
                "isTransient", fieldNode.isTransient(),
                "isVolatile", fieldNode.isVolatile(),
                "annotations", fieldNode.getAnnotations() != null ? fieldNode.getAnnotations() : Collections.emptyList(),
                "initialValue", fieldNode.getInitialValue(),
                "javadoc", fieldNode.getJavadoc()
        );

        tx.run(cypher, params);
    }

    private void storeRelationship(org.neo4j.driver.TransactionContext tx, CodeRelationship rel) {
        // ✅ CYPHER INJECTION SAFETY: Why this String.format is safe
        // ──────────────────────────────────────────────────────────────────────────
        // This uses String.format() to inject the relationship type, but it's SAFE because:
        //
        // 1. rel.getType() returns a DependencyEdge.RelationshipType enum
        // 2. Enums have a fixed, compile-time set of values (INJECTS, RETURNS, ACCEPTS, etc.)
        // 3. Users CANNOT inject arbitrary values through enum types
        //
        // WHY NOT use a parameter ($relType)?
        // Neo4j Cypher does NOT support parameterized relationship types:
        //   ✗ Invalid: MERGE (from)-[r:$relType]->(to)
        //   ✓ Valid:   MERGE (from)-[r:EXTENDS]->(to)
        //
        // DEFENSIVE VALIDATION: We validate that type is from the enum
        if (rel.getType() == null) {
            throw new IllegalArgumentException("Relationship type cannot be null");
        }
        // .name() will throw NPE if type is null (defensive check above prevents this)
        String relationshipType = rel.getType().name();  // Guaranteed to be enum value

        String cypher = String.format("""
            MATCH (from {id: $fromId})
            MATCH (to {id: $toId})
            MERGE (from)-[r:%s]->(to)
            SET r.sourceFile = $sourceFile,
                r.lineNumber = $lineNumber
            """, relationshipType);

        Map<String, Object> params = createParams(
                "fromId", rel.getFromId(),
                "toId", rel.getToId(),
                "sourceFile", rel.getSourceFile(),
                "lineNumber", rel.getLineNumber()
        );

        try {
            tx.run(cypher, params);
        } catch (Exception e) {
            log.debug("Failed to create relationship {} -> {}: {}", rel.getFromId(), rel.getToId(), e.getMessage());
        }
    }

    // ================================================================
    // QUERY METHODS - ORIGINAL SIGNATURES PRESERVED
    // ================================================================

    /**
     * Find all dependencies of a class.
     * ORIGINAL SIGNATURE - Returns List<ClassNode>
     */
    public List<ClassNode> findClassDependencies(String fullyQualifiedClassName) {
        String cypher = """
            MATCH (c:Class {fullyQualifiedName: $fqn})-[:EXTENDS|IMPLEMENTS|TYPE_DEPENDENCY*1..2]->(dep:Class)
            RETURN DISTINCT dep
            """;

        try (Session session = driver.session()) {
            // ✅ FIX: Use executeRead instead of deprecated readTransaction
            return session.executeRead(tx -> {
                Result result = tx.run(cypher, Collections.singletonMap("fqn", fullyQualifiedClassName));
                return result.stream()
                        .map(record -> nodeToClassNode(record.get("dep").asNode()))
                        .collect(Collectors.toList());
            });
        }
    }

    /**
     * Find all methods that call a specific method.
     * ORIGINAL SIGNATURE - Takes methodName (not FQN), Returns List<MethodNode>
     */
    public List<MethodNode> findMethodCallers(String methodName) {
        String cypher = """
            MATCH (caller:Method)-[:CALLS]->(m:Method)
            WHERE m.name = $methodName
            RETURN DISTINCT caller
            """;

        try (Session session = driver.session()) {
            // ✅ FIX: Use executeRead instead of deprecated readTransaction
            return session.executeRead(tx -> {
                Result result = tx.run(cypher, Collections.singletonMap("methodName", methodName));
                return result.stream()
                        .map(record -> nodeToMethodNode(record.get("caller").asNode()))
                        .collect(Collectors.toList());
            });
        }
    }

    /**
     * Find all subclasses of a class.
     * ORIGINAL SIGNATURE - Returns List<ClassNode>
     */
    public List<ClassNode> findSubclasses(String fullyQualifiedClassName) {
        String cypher = """
            MATCH (subclass:Class)-[:EXTENDS]->(c:Class {fullyQualifiedName: $fqn})
            RETURN DISTINCT subclass
            """;

        try (Session session = driver.session()) {
            // ✅ FIX: Use executeRead instead of deprecated readTransaction
            return session.executeRead(tx -> {
                Result result = tx.run(cypher, Collections.singletonMap("fqn", fullyQualifiedClassName));
                return result.stream()
                        .map(record -> nodeToClassNode(record.get("subclass").asNode()))
                        .collect(Collectors.toList());
            });
        }
    }

    /**
     * Find all methods in a class.
     * ORIGINAL SIGNATURE - Returns List<MethodNode>
     */
    public List<MethodNode> findMethodsInClass(String fullyQualifiedClassName) {
        String cypher = """
            MATCH (c:Class {fullyQualifiedName: $fqn})-[:HAS_METHOD]->(m:Method)
            RETURN m
            """;

        try (Session session = driver.session()) {
            // ✅ FIX: Use executeRead instead of deprecated readTransaction
            return session.executeRead(tx -> {
                Result result = tx.run(cypher, Collections.singletonMap("fqn", fullyQualifiedClassName));
                return result.stream()
                        .map(record -> nodeToMethodNode(record.get("m").asNode()))
                        .collect(Collectors.toList());
            });
        }
    }

    /**
     * Find all fields in a class.
     * ORIGINAL SIGNATURE - Returns List<FieldNode>
     */
    public List<FieldNode> findFieldsInClass(String fullyQualifiedClassName) {
        String cypher = """
            MATCH (c:Class {fullyQualifiedName: $fqn})-[:HAS_FIELD]->(f:Field)
            RETURN f
            """;

        try (Session session = driver.session()) {
            // ✅ FIX: Use executeRead instead of deprecated readTransaction
            return session.executeRead(tx -> {
                Result result = tx.run(cypher, Collections.singletonMap("fqn", fullyQualifiedClassName));
                return result.stream()
                        .map(record -> nodeToFieldNode(record.get("f").asNode()))
                        .collect(Collectors.toList());
            });
        }
    }

    /**
     * Find class by Neo4j node ID.
     */
    public ClassNode findClassById(String id) {
        String cypher = "MATCH (c:Class {id: $id}) RETURN c";

        try (Session session = driver.session()) {
            // ✅ FIX: Use executeRead instead of deprecated readTransaction
            return session.executeRead(tx -> {
                Result result = tx.run(cypher, Collections.singletonMap("id", id));
                if (result.hasNext()) {
                    return nodeToClassNode(result.single().get("c").asNode());
                }
                return null;
            });
        }
    }

    /**
     * Clear all code graph data (for reindexing).
     *
     * ⚠️⚠️⚠️ CRITICAL WARNING - DATABASE WIPE DANGER ⚠️⚠️⚠️
     * ════════════════════════════════════════════════════════════════════════
     * This method deletes EVERYTHING in the Neo4j database!
     *
     * ❌ DOES NOT delete just one repository
     * ❌ DOES NOT support multi-repo (all repos share the same Neo4j graph)
     * ✓ Deletes ALL classes, methods, fields, and relationships for ALL repos
     *
     * Known limitation:
     * - Neo4j nodes don't currently have a repoName property
     * - Cannot selectively delete data for a single repository
     * - All indexed repositories are mixed together in the same graph
     *
     * Use cases:
     * - Complete database reset during development
     * - Disaster recovery / corruption cleanup
     *
     * DO NOT CALL IN PRODUCTION unless you want to lose ALL indexed code!
     *
     * @deprecated This method is dangerous and should be rarely used.
     *             Consider adding repoName to all nodes to enable repo-specific deletion.
     * ════════════════════════════════════════════════════════════════════════
     */
    @Deprecated(since = "1.0", forRemoval = true)
    public void clearAll() {
        log.error("⚠️⚠️⚠️ DANGER: Deleting ALL data from Neo4j database! ⚠️⚠️⚠️");
        log.error("This will delete ALL repositories, ALL classes, ALL methods, ALL relationships!");
        log.error("If you only want to clear ONE repository, this method cannot do that.");
        log.error("You must re-index ALL repositories after calling this method.");

        try (Session session = driver.session()) {
            long startTime = System.currentTimeMillis();

            // Count nodes before deletion (for logging)
            Result countResult = session.run("MATCH (n) RETURN count(n) as count");
            long nodeCount = countResult.single().get("count").asLong();

            log.warn("Deleting {} nodes from Neo4j...", nodeCount);

            // Delete everything
            session.run("MATCH (n) DETACH DELETE n");

            long duration = System.currentTimeMillis() - startTime;
            log.warn("✅ Deleted {} nodes in {}ms. Database is now empty.", nodeCount, duration);
        } catch (Exception e) {
            log.error("Failed to clear Neo4j database", e);
            throw new RuntimeException("Failed to clear Neo4j database", e);
        }
    }

    // TODO: Add repo-specific deletion once repoName is added to all nodes
    // public void clearRepo(String repoName) {
    //     session.run("MATCH (n {repoName: $repoName}) DETACH DELETE n",
    //                 Collections.singletonMap("repoName", repoName));
    // }

    // ================================================================
    // CONVERSION METHODS
    // ================================================================

    private ClassNode nodeToClassNode(Node node) {
        return ClassNode.builder()
                .id(getStringValue(node, "id"))
                .name(getStringValue(node, "name"))
                .fullyQualifiedName(getStringValue(node, "fullyQualifiedName"))
                .packageName(getStringValue(node, "packageName"))
                .sourceFilePath(getStringValue(node, "sourceFilePath"))
                .startLine(getIntValue(node, "startLine"))
                .endLine(getIntValue(node, "endLine"))
                .sourceCode(getStringValue(node, "sourceCode"))
                .build();
    }

    private MethodNode nodeToMethodNode(Node node) {
        return MethodNode.builder()
                .id(getStringValue(node, "id"))
                .name(getStringValue(node, "name"))
                .fullyQualifiedName(getStringValue(node, "fullyQualifiedName"))
                .className(getStringValue(node, "className"))
                .sourceFilePath(getStringValue(node, "sourceFilePath"))
                .startLine(getIntValue(node, "startLine"))
                .endLine(getIntValue(node, "endLine"))
                .returnType(getStringValue(node, "returnType"))
                .sourceCode(getStringValue(node, "sourceCode"))
                .build();
    }

    private FieldNode nodeToFieldNode(Node node) {
        return FieldNode.builder()
                .id(getStringValue(node, "id"))
                .name(getStringValue(node, "name"))
                .fullyQualifiedName(getStringValue(node, "fullyQualifiedName"))
                .className(getStringValue(node, "className"))
                .type(getStringValue(node, "type"))
                .build();
    }

    private String getStringValue(Node node, String key) {
        return node.containsKey(key) && !node.get(key).isNull()
                ? node.get(key).asString()
                : "";
    }

    private int getIntValue(Node node, String key) {
        return node.containsKey(key) && !node.get(key).isNull()
                ? node.get(key).asInt()
                : 0;
    }
}