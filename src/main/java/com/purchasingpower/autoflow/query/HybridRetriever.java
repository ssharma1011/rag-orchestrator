package com.purchasingpower.autoflow.query;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.purchasingpower.autoflow.model.neo4j.ClassNode;
import com.purchasingpower.autoflow.model.neo4j.MethodNode;
import com.purchasingpower.autoflow.model.neo4j.FieldNode;
import com.purchasingpower.autoflow.model.retrieval.HybridRetrievalResult;
import com.purchasingpower.autoflow.storage.Neo4jGraphStore;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRetriever {

    private final Neo4jGraphStore neo4jStore;

    public HybridRetrievalResult retrieve(String query,
                                          QueryResponseWithUnsignedIndices pineconeResults,
                                          int expandDepth) {
        log.info("Hybrid retrieval: query='{}', expandDepth={}", query, expandDepth);
        HybridRetrievalResult result = new HybridRetrievalResult();

        List<String> classIds = extractClassIdsFromPinecone(pineconeResults);

        for (String classId : classIds) {
            ClassNode classNode = neo4jStore.findClassById(classId);
            if (classNode != null) {
                result.addClass(classNode);
                if (isDependencyQuery(query)) expandDependencies(classNode, result, expandDepth);
                else if (isCallerQuery(query)) expandCallers(classNode, result, expandDepth);
                else if (isHierarchyQuery(query)) expandHierarchy(classNode, result, expandDepth);
                else expandClassStructure(classNode, result);
            }
        }
        return result;
    }

    private List<String> extractClassIdsFromPinecone(QueryResponseWithUnsignedIndices pineconeResults) {
        List<String> classIds = new ArrayList<>();
        if (pineconeResults.getMatchesList() != null) {
            for (ScoredVectorWithUnsignedIndices match : pineconeResults.getMatchesList()) {
                if (match.getMetadata() != null) {
                    // FIX: Correctly access Protobuf Struct metadata
                    Struct metadata = match.getMetadata();
                    Map<String, Value> fields = metadata.getFieldsMap();
                    if (fields.containsKey("neo4j_node_id")) {
                        classIds.add(fields.get("neo4j_node_id").getStringValue());
                    }
                }
            }
        }
        return classIds;
    }

    private void expandDependencies(ClassNode classNode, HybridRetrievalResult result, int depth) {
        if (depth <= 0) return;
        List<ClassNode> dependencies = neo4jStore.findClassDependencies(classNode.getFullyQualifiedName());
        for (ClassNode dep : dependencies) {
            if (!result.hasClass(dep.getId())) {
                result.addClass(dep);
                if (depth > 1) expandDependencies(dep, result, depth - 1);
            }
        }
    }

    private void expandCallers(ClassNode classNode, HybridRetrievalResult result, int depth) {
        if (depth <= 0) return;
        List<MethodNode> methods = neo4jStore.findMethodsInClass(classNode.getFullyQualifiedName());
        for (MethodNode method : methods) {
            result.addMethod(method);
            List<MethodNode> callers = neo4jStore.findMethodCallers(method.getName());
            for (MethodNode caller : callers) {
                if (!result.hasMethod(caller.getId())) result.addMethod(caller);
            }
        }
    }

    private void expandHierarchy(ClassNode classNode, HybridRetrievalResult result, int depth) {
        if (depth <= 0) return;
        List<ClassNode> subclasses = neo4jStore.findSubclasses(classNode.getFullyQualifiedName());
        for (ClassNode subclass : subclasses) {
            if (!result.hasClass(subclass.getId())) {
                result.addClass(subclass);
                if (depth > 1) expandHierarchy(subclass, result, depth - 1);
            }
        }
    }

    private void expandClassStructure(ClassNode classNode, HybridRetrievalResult result) {
        String fqn = classNode.getFullyQualifiedName();
        neo4jStore.findMethodsInClass(fqn).forEach(result::addMethod);
        neo4jStore.findFieldsInClass(fqn).forEach(result::addField);
    }

    private boolean isDependencyQuery(String query) {
        String q = query.toLowerCase();
        return q.contains("depend") || q.contains("use") || q.contains("import");
    }

    private boolean isCallerQuery(String query) {
        String q = query.toLowerCase();
        return q.contains("call") || q.contains("invoke") || q.contains("who uses");
    }

    private boolean isHierarchyQuery(String query) {
        String q = query.toLowerCase();
        return q.contains("subclass") || q.contains("extend") || q.contains("implement");
    }
}