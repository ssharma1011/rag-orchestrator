package com.purchasingpower.autoflow.query;

import com.purchasingpower.autoflow.model.neo4j.ClassNode;
import com.purchasingpower.autoflow.model.neo4j.MethodNode;
import com.purchasingpower.autoflow.model.neo4j.FieldNode;
import com.purchasingpower.autoflow.storage.Neo4jGraphStore;
import io.pinecone.clients.Index;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * THE SOLUTION TO THE CHUNKING PROBLEM!
 *
 * Hybrid Retrieval Strategy:
 * 1. Semantic Search (Pinecone): Find relevant code chunks by meaning
 * 2. Graph Expansion (Neo4j): Add related code via relationships
 * 3. Result: Complete context with NO broken relationships!
 *
 * Example Query: "What does PaymentService depend on?"
 *
 * OLD (BROKEN) APPROACH:
 * - Pinecone finds chunks mentioning "PaymentService"
 * - Chunks may be split: Chunk1 has fields, Chunk2 has method calls
 * - RESULT: Missing dependencies!
 *
 * NEW (HYBRID) APPROACH:
 * - Pinecone finds PaymentService class
 * - Neo4j expands with: EXTENDS, USES, CALLS relationships
 * - RESULT: Complete dependency graph!
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRetriever {

    private final Neo4jGraphStore neo4jStore;

    /**
     * Hybrid retrieval: Combine Pinecone semantic search with Neo4j graph traversal.
     *
     * @param query User's query
     * @param pineconeResults Results from Pinecone vector search
     * @param expandDepth How many graph hops to expand (1-2 recommended)
     * @return Complete code context with relationships preserved
     */
    public HybridRetrievalResult retrieve(String query,
                                           QueryResponseWithUnsignedIndices pineconeResults,
                                           int expandDepth) {
        log.info("Hybrid retrieval: query='{}', expandDepth={}", query, expandDepth);

        HybridRetrievalResult result = new HybridRetrievalResult();

        // Step 1: Extract Neo4j node IDs from Pinecone metadata
        List<String> classIds = extractClassIdsFromPinecone(pineconeResults);
        log.info("Found {} classes from Pinecone", classIds.size());

        // Step 2: For each class, expand via Neo4j relationships
        for (String classId : classIds) {
            ClassNode classNode = neo4jStore.findClassById(classId);
            if (classNode != null) {
                result.addClass(classNode);

                // Expand dependencies based on query intent
                if (isDependencyQuery(query)) {
                    expandDependencies(classNode, result, expandDepth);
                } else if (isCallerQuery(query)) {
                    expandCallers(classNode, result, expandDepth);
                } else if (isHierarchyQuery(query)) {
                    expandHierarchy(classNode, result, expandDepth);
                } else {
                    // Default: get full class structure
                    expandClassStructure(classNode, result);
                }
            }
        }

        log.info("Hybrid retrieval complete: {} classes, {} methods, {} fields",
                result.getClasses().size(), result.getMethods().size(), result.getFields().size());

        return result;
    }

    /**
     * Extract class IDs from Pinecone search results.
     * Assumes Pinecone metadata contains "neo4j_node_id" field.
     */
    private List<String> extractClassIdsFromPinecone(QueryResponseWithUnsignedIndices pineconeResults) {
        List<String> classIds = new ArrayList<>();

        if (pineconeResults.getMatchesList() != null) {
            for (ScoredVectorWithUnsignedIndices match : pineconeResults.getMatchesList()) {
                if (match.getMetadata() != null && match.getMetadata().containsKey("neo4j_node_id")) {
                    String nodeId = match.getMetadata().get("neo4j_node_id").toString();
                    classIds.add(nodeId);
                }
            }
        }

        return classIds;
    }

    /**
     * Expand dependencies: EXTENDS, IMPLEMENTS, TYPE_DEPENDENCY
     */
    private void expandDependencies(ClassNode classNode, HybridRetrievalResult result, int depth) {
        if (depth <= 0) return;

        String fqn = classNode.getFullyQualifiedName();
        List<ClassNode> dependencies = neo4jStore.findClassDependencies(fqn);

        for (ClassNode dep : dependencies) {
            if (!result.hasClass(dep.getId())) {
                result.addClass(dep);
                // Recursive expansion
                if (depth > 1) {
                    expandDependencies(dep, result, depth - 1);
                }
            }
        }
    }

    /**
     * Expand callers: Find all methods that call methods in this class
     */
    private void expandCallers(ClassNode classNode, HybridRetrievalResult result, int depth) {
        if (depth <= 0) return;

        // Get all methods in this class
        List<MethodNode> methods = neo4jStore.findMethodsInClass(classNode.getFullyQualifiedName());

        for (MethodNode method : methods) {
            result.addMethod(method);

            // Find callers of this method
            List<MethodNode> callers = neo4jStore.findMethodCallers(method.getName());
            for (MethodNode caller : callers) {
                if (!result.hasMethod(caller.getId())) {
                    result.addMethod(caller);
                }
            }
        }
    }

    /**
     * Expand hierarchy: EXTENDS, subclasses
     */
    private void expandHierarchy(ClassNode classNode, HybridRetrievalResult result, int depth) {
        if (depth <= 0) return;

        String fqn = classNode.getFullyQualifiedName();

        // Find subclasses
        List<ClassNode> subclasses = neo4jStore.findSubclasses(fqn);
        for (ClassNode subclass : subclasses) {
            if (!result.hasClass(subclass.getId())) {
                result.addClass(subclass);
                // Recursive expansion
                if (depth > 1) {
                    expandHierarchy(subclass, result, depth - 1);
                }
            }
        }
    }

    /**
     * Expand class structure: Get all methods and fields
     */
    private void expandClassStructure(ClassNode classNode, HybridRetrievalResult result) {
        String fqn = classNode.getFullyQualifiedName();

        // Get all methods
        List<MethodNode> methods = neo4jStore.findMethodsInClass(fqn);
        methods.forEach(result::addMethod);

        // Get all fields
        List<FieldNode> fields = neo4jStore.findFieldsInClass(fqn);
        fields.forEach(result::addField);
    }

    /**
     * Detect dependency queries like "what depends on", "what uses", etc.
     */
    private boolean isDependencyQuery(String query) {
        String lowerQuery = query.toLowerCase();
        return lowerQuery.contains("depend") ||
               lowerQuery.contains("use") ||
               lowerQuery.contains("import");
    }

    /**
     * Detect caller queries like "what calls", "who uses", etc.
     */
    private boolean isCallerQuery(String query) {
        String lowerQuery = query.toLowerCase();
        return lowerQuery.contains("call") ||
               lowerQuery.contains("invoke") ||
               lowerQuery.contains("who uses");
    }

    /**
     * Detect hierarchy queries like "subclasses", "extends", etc.
     */
    private boolean isHierarchyQuery(String query) {
        String lowerQuery = query.toLowerCase();
        return lowerQuery.contains("subclass") ||
               lowerQuery.contains("extend") ||
               lowerQuery.contains("implement") ||
               lowerQuery.contains("hierarchy");
    }

    /**
     * Result container for hybrid retrieval
     */
    public static class HybridRetrievalResult {
        private final Map<String, ClassNode> classes = new LinkedHashMap<>();
        private final Map<String, MethodNode> methods = new LinkedHashMap<>();
        private final Map<String, FieldNode> fields = new LinkedHashMap<>();

        public void addClass(ClassNode classNode) {
            classes.put(classNode.getId(), classNode);
        }

        public void addMethod(MethodNode methodNode) {
            methods.put(methodNode.getId(), methodNode);
        }

        public void addField(FieldNode fieldNode) {
            fields.put(fieldNode.getId(), fieldNode);
        }

        public boolean hasClass(String id) {
            return classes.containsKey(id);
        }

        public boolean hasMethod(String id) {
            return methods.containsKey(id);
        }

        public List<ClassNode> getClasses() {
            return new ArrayList<>(classes.values());
        }

        public List<MethodNode> getMethods() {
            return new ArrayList<>(methods.values());
        }

        public List<FieldNode> getFields() {
            return new ArrayList<>(fields.values());
        }

        /**
         * Format result as context string for LLM
         */
        public String toContextString() {
            StringBuilder sb = new StringBuilder();

            sb.append("=== CODE CONTEXT ===\n\n");

            // Classes
            if (!classes.isEmpty()) {
                sb.append("CLASSES:\n");
                for (ClassNode cls : classes.values()) {
                    sb.append(String.format("- %s (package: %s, type: %s)\n",
                            cls.getName(),
                            cls.getPackageName(),
                            cls.getClassType()));
                    if (cls.getSuperClassName() != null) {
                        sb.append(String.format("  extends: %s\n", cls.getSuperClassName()));
                    }
                    if (!cls.getInterfaces().isEmpty()) {
                        sb.append(String.format("  implements: %s\n", String.join(", ", cls.getInterfaces())));
                    }
                    sb.append("\n");
                }
            }

            // Methods
            if (!methods.isEmpty()) {
                sb.append("\nMETHODS:\n");
                for (MethodNode method : methods.values()) {
                    sb.append(String.format("- %s.%s -> %s\n",
                            method.getClassName(),
                            method.getName(),
                            method.getReturnType()));
                    sb.append(String.format("  Source: %s:%d-%d\n",
                            method.getSourceFilePath(),
                            method.getStartLine(),
                            method.getEndLine()));
                    sb.append("\n");
                }
            }

            // Fields
            if (!fields.isEmpty()) {
                sb.append("\nFIELDS:\n");
                for (FieldNode field : fields.values()) {
                    sb.append(String.format("- %s.%s : %s\n",
                            field.getClassName(),
                            field.getName(),
                            field.getType()));
                }
            }

            return sb.toString();
        }

        /**
         * Get full source code of all entities (for detailed context)
         */
        public String getFullSourceCode() {
            StringBuilder sb = new StringBuilder();

            for (ClassNode cls : classes.values()) {
                sb.append("// ").append(cls.getSourceFilePath()).append("\n");
                sb.append(cls.getSourceCode()).append("\n\n");
            }

            return sb.toString();
        }

        public int getTotalEntities() {
            return classes.size() + methods.size() + fields.size();
        }
    }
}
