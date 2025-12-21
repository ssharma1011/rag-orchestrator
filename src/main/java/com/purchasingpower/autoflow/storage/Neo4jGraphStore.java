package com.purchasingpower.autoflow.storage;

import com.purchasingpower.autoflow.model.neo4j.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Neo4j Knowledge Graph Store for code entities and relationships.
 *
 * This is the SOLUTION to the chunking problem:
 * - Stores code as a graph (classes, methods, fields as nodes)
 * - Preserves ALL relationships (extends, calls, uses, etc.)
 * - Enables structural queries like "what calls this method?"
 *
 * Combined with Pinecone (semantic search), provides complete Code RAG.
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

    @PostConstruct
    public void init() {
        log.info("Connecting to Neo4j at: {}", neo4jUri);
        driver = GraphDatabase.driver(neo4jUri,
                AuthTokens.basic(neo4jUsername, neo4jPassword));

        // Create indexes for performance
        createIndexes();
    }

    @PreDestroy
    public void close() {
        if (driver != null) {
            driver.close();
            log.info("Neo4j connection closed");
        }
    }

    /**
     * Create indexes on frequently queried properties
     */
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

    /**
     * Store a complete parsed code graph in Neo4j.
     * This stores all classes, methods, fields, and their relationships.
     */
    public void storeCodeGraph(ParsedCodeGraph graph) {
        log.info("Storing code graph: {} classes, {} methods, {} relationships",
                graph.getClasses().size(), graph.getMethods().size(), graph.getTotalRelationships());

        try (Session session = driver.session()) {
            session.writeTransaction(tx -> {
                // Store all classes
                for (ClassNode classNode : graph.getClasses()) {
                    storeClassNode(tx, classNode);
                }

                // Store all methods
                for (MethodNode methodNode : graph.getMethods()) {
                    storeMethodNode(tx, methodNode);
                }

                // Store all fields
                for (FieldNode fieldNode : graph.getFields()) {
                    storeFieldNode(tx, fieldNode);
                }

                // Store all relationships
                for (CodeRelationship rel : graph.getRelationships()) {
                    storeRelationship(tx, rel);
                }

                return null;
            });

            log.info("Code graph stored successfully");
        } catch (Exception e) {
            log.error("Failed to store code graph", e);
            throw new RuntimeException("Failed to store code graph", e);
        }
    }

    /**
     * Store a single class node in Neo4j
     */
    private void storeClassNode(Transaction tx, ClassNode classNode) {
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

        Map<String, Object> params = Map.ofEntries(
                Map.entry("id", classNode.getId()),
                Map.entry("name", classNode.getName()),
                Map.entry("fqn", classNode.getFullyQualifiedName()),
                Map.entry("packageName", classNode.getPackageName()),
                Map.entry("sourceFilePath", classNode.getSourceFilePath()),
                Map.entry("startLine", classNode.getStartLine()),
                Map.entry("endLine", classNode.getEndLine()),
                Map.entry("classType", classNode.getClassType().name()),
                Map.entry("superClassName", classNode.getSuperClassName()),
                Map.entry("interfaces", classNode.getInterfaces()),
                Map.entry("accessModifier", classNode.getAccessModifier()),
                Map.entry("isAbstract", classNode.isAbstract()),
                Map.entry("isFinal", classNode.isFinal()),
                Map.entry("isStatic", classNode.isStatic()),
                Map.entry("annotations", classNode.getAnnotations()),
                Map.entry("sourceCode", classNode.getSourceCode()),
                Map.entry("javadoc", classNode.getJavadoc())
        );

        tx.run(cypher, params);
    }

    /**
     * Store a single method node in Neo4j
     */
    private void storeMethodNode(Transaction tx, MethodNode methodNode) {
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

        Map<String, Object> params = Map.ofEntries(
                Map.entry("id", methodNode.getId()),
                Map.entry("name", methodNode.getName()),
                Map.entry("fqn", methodNode.getFullyQualifiedName()),
                Map.entry("className", methodNode.getClassName()),
                Map.entry("sourceFilePath", methodNode.getSourceFilePath()),
                Map.entry("startLine", methodNode.getStartLine()),
                Map.entry("endLine", methodNode.getEndLine()),
                Map.entry("returnType", methodNode.getReturnType()),
                Map.entry("accessModifier", methodNode.getAccessModifier()),
                Map.entry("isStatic", methodNode.isStatic()),
                Map.entry("isFinal", methodNode.isFinal()),
                Map.entry("isAbstract", methodNode.isAbstract()),
                Map.entry("isSynchronized", methodNode.isSynchronized()),
                Map.entry("isConstructor", methodNode.isConstructor()),
                Map.entry("annotations", methodNode.getAnnotations()),
                Map.entry("sourceCode", methodNode.getSourceCode()),
                Map.entry("javadoc", methodNode.getJavadoc()),
                Map.entry("thrownExceptions", methodNode.getThrownExceptions())
        );

        tx.run(cypher, params);
    }

    /**
     * Store a single field node in Neo4j
     */
    private void storeFieldNode(Transaction tx, FieldNode fieldNode) {
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

        Map<String, Object> params = Map.ofEntries(
                Map.entry("id", fieldNode.getId()),
                Map.entry("name", fieldNode.getName()),
                Map.entry("fqn", fieldNode.getFullyQualifiedName()),
                Map.entry("className", fieldNode.getClassName()),
                Map.entry("sourceFilePath", fieldNode.getSourceFilePath()),
                Map.entry("lineNumber", fieldNode.getLineNumber()),
                Map.entry("type", fieldNode.getType()),
                Map.entry("accessModifier", fieldNode.getAccessModifier()),
                Map.entry("isStatic", fieldNode.isStatic()),
                Map.entry("isFinal", fieldNode.isFinal()),
                Map.entry("isTransient", fieldNode.isTransient()),
                Map.entry("isVolatile", fieldNode.isVolatile()),
                Map.entry("annotations", fieldNode.getAnnotations()),
                Map.entry("initialValue", fieldNode.getInitialValue()),
                Map.entry("javadoc", fieldNode.getJavadoc())
        );

        tx.run(cypher, params);
    }

    /**
     * Store a relationship between code entities
     */
    private void storeRelationship(Transaction tx, CodeRelationship rel) {
        // Create relationship with dynamic relationship type
        String cypher = String.format("""
            MATCH (from {id: $fromId})
            MATCH (to {id: $toId})
            MERGE (from)-[r:%s]->(to)
            SET r.sourceFile = $sourceFile,
                r.lineNumber = $lineNumber
            """, rel.getType().name());

        Map<String, Object> params = Map.of(
                "fromId", rel.getFromId(),
                "toId", rel.getToId(),
                "sourceFile", rel.getSourceFile(),
                "lineNumber", rel.getLineNumber()
        );

        try {
            tx.run(cypher, params);
        } catch (Exception e) {
            // Relationship may fail if target node doesn't exist (e.g., external class)
            log.debug("Failed to create relationship {} -> {}: {}", rel.getFromId(), rel.getToId(), e.getMessage());
        }
    }

    // ================================================================
    // QUERY METHODS - These solve the chunking problem!
    // ================================================================

    /**
     * Find all dependencies of a class.
     * Answers: "What does PaymentService depend on?"
     */
    public List<ClassNode> findClassDependencies(String fullyQualifiedClassName) {
        String cypher = """
            MATCH (c:Class {fullyQualifiedName: $fqn})-[:EXTENDS|IMPLEMENTS|TYPE_DEPENDENCY*1..2]->(dep:Class)
            RETURN DISTINCT dep
            """;

        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                Result result = tx.run(cypher, Map.of("fqn", fullyQualifiedClassName));
                return result.stream()
                        .map(record -> nodeToClassNode(record.get("dep").asNode()))
                        .collect(Collectors.toList());
            });
        }
    }

    /**
     * Find all methods that call a specific method.
     * Answers: "What calls authenticateUser()?"
     *
     * THIS IS THE KEY QUERY - NO MORE CHUNKING PROBLEM!
     */
    public List<MethodNode> findMethodCallers(String methodName) {
        String cypher = """
            MATCH (caller:Method)-[:CALLS]->(m:Method)
            WHERE m.name = $methodName
            RETURN DISTINCT caller
            """;

        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                Result result = tx.run(cypher, Map.of("methodName", methodName));
                return result.stream()
                        .map(record -> nodeToMethodNode(record.get("caller").asNode()))
                        .collect(Collectors.toList());
            });
        }
    }

    /**
     * Find all subclasses of a class.
     * Answers: "What extends AbstractService?"
     */
    public List<ClassNode> findSubclasses(String fullyQualifiedClassName) {
        String cypher = """
            MATCH (subclass:Class)-[:EXTENDS]->(c:Class {fullyQualifiedName: $fqn})
            RETURN DISTINCT subclass
            """;

        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                Result result = tx.run(cypher, Map.of("fqn", fullyQualifiedClassName));
                return result.stream()
                        .map(record -> nodeToClassNode(record.get("subclass").asNode()))
                        .collect(Collectors.toList());
            });
        }
    }

    /**
     * Find all methods in a class.
     */
    public List<MethodNode> findMethodsInClass(String fullyQualifiedClassName) {
        String cypher = """
            MATCH (c:Class {fullyQualifiedName: $fqn})-[:HAS_METHOD]->(m:Method)
            RETURN m
            """;

        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                Result result = tx.run(cypher, Map.of("fqn", fullyQualifiedClassName));
                return result.stream()
                        .map(record -> nodeToMethodNode(record.get("m").asNode()))
                        .collect(Collectors.toList());
            });
        }
    }

    /**
     * Find all fields in a class.
     */
    public List<FieldNode> findFieldsInClass(String fullyQualifiedClassName) {
        String cypher = """
            MATCH (c:Class {fullyQualifiedName: $fqn})-[:HAS_FIELD]->(f:Field)
            RETURN f
            """;

        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                Result result = tx.run(cypher, Map.of("fqn", fullyQualifiedClassName));
                return result.stream()
                        .map(record -> nodeToFieldNode(record.get("f").asNode()))
                        .collect(Collectors.toList());
            });
        }
    }

    /**
     * Find class by Neo4j node ID (for linking with Pinecone metadata).
     */
    public ClassNode findClassById(String id) {
        String cypher = "MATCH (c:Class {id: $id}) RETURN c";

        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                Result result = tx.run(cypher, Map.of("id", id));
                if (result.hasNext()) {
                    return nodeToClassNode(result.single().get("c").asNode());
                }
                return null;
            });
        }
    }

    /**
     * Clear all code graph data (for reindexing)
     */
    public void clearAll() {
        log.warn("CLEARING ALL NEO4J DATA");
        try (Session session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n");
        }
    }

    // ================================================================
    // CONVERSION METHODS
    // ================================================================

    private ClassNode nodeToClassNode(Node node) {
        return ClassNode.builder()
                .id(node.get("id").asString())
                .name(node.get("name").asString())
                .fullyQualifiedName(node.get("fullyQualifiedName").asString())
                .packageName(node.get("packageName").asString(""))
                .sourceFilePath(node.get("sourceFilePath").asString(""))
                .startLine(node.get("startLine").asInt(0))
                .endLine(node.get("endLine").asInt(0))
                .sourceCode(node.get("sourceCode").asString(""))
                .build();
    }

    private MethodNode nodeToMethodNode(Node node) {
        return MethodNode.builder()
                .id(node.get("id").asString())
                .name(node.get("name").asString())
                .fullyQualifiedName(node.get("fullyQualifiedName").asString())
                .className(node.get("className").asString(""))
                .sourceFilePath(node.get("sourceFilePath").asString(""))
                .startLine(node.get("startLine").asInt(0))
                .endLine(node.get("endLine").asInt(0))
                .returnType(node.get("returnType").asString(""))
                .sourceCode(node.get("sourceCode").asString(""))
                .build();
    }

    private FieldNode nodeToFieldNode(Node node) {
        return FieldNode.builder()
                .id(node.get("id").asString())
                .name(node.get("name").asString())
                .fullyQualifiedName(node.get("fullyQualifiedName").asString())
                .className(node.get("className").asString(""))
                .type(node.get("type").asString(""))
                .build();
    }
}
