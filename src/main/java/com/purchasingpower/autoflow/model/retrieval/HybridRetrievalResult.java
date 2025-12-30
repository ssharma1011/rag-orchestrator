package com.purchasingpower.autoflow.model.retrieval;

import com.purchasingpower.autoflow.model.neo4j.ClassNode;
import com.purchasingpower.autoflow.model.neo4j.FieldNode;
import com.purchasingpower.autoflow.model.neo4j.MethodNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of hybrid retrieval combining semantic search with graph traversal.
 *
 * Stores classes, methods, and fields retrieved through both Pinecone
 * semantic search and Neo4j knowledge graph expansion, maintaining
 * insertion order for relevance.
 *
 * @see com.purchasingpower.autoflow.query.HybridRetriever
 */
public class HybridRetrievalResult {
    private final Map<String, ClassNode> classes = new LinkedHashMap<>();
    private final Map<String, MethodNode> methods = new LinkedHashMap<>();
    private final Map<String, FieldNode> fields = new LinkedHashMap<>();

    public void addClass(ClassNode node) {
        classes.put(node.getId(), node);
    }

    public void addMethod(MethodNode node) {
        methods.put(node.getId(), node);
    }

    public void addField(FieldNode node) {
        fields.put(node.getId(), node);
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

    public String toContextString() {
        StringBuilder sb = new StringBuilder("=== CODE CONTEXT ===\n\n");
        for (ClassNode c : classes.values()) {
            sb.append("Class: ").append(c.getName()).append("\n");
        }
        return sb.toString();
    }

    public String getFullSourceCode() {
        StringBuilder sb = new StringBuilder();
        for (ClassNode c : classes.values()) {
            sb.append(c.getSourceCode()).append("\n");
        }
        return sb.toString();
    }
}
