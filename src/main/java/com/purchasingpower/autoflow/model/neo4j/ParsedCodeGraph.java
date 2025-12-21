package com.purchasingpower.autoflow.model.neo4j;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Container for all extracted code entities and relationships from parsing.
 * This is the output of EntityExtractor and input to Neo4jGraphStore.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedCodeGraph {

    /**
     * Source file that was parsed
     */
    private String sourceFilePath;

    /**
     * All classes/interfaces found in the file
     */
    @Builder.Default
    private List<ClassNode> classes = new ArrayList<>();

    /**
     * All methods found in the file
     */
    @Builder.Default
    private List<MethodNode> methods = new ArrayList<>();

    /**
     * All fields found in the file
     */
    @Builder.Default
    private List<FieldNode> fields = new ArrayList<>();

    /**
     * All relationships between entities
     */
    @Builder.Default
    private List<CodeRelationship> relationships = new ArrayList<>();

    /**
     * Any parsing errors encountered
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    /**
     * Get total number of entities extracted
     */
    public int getTotalEntities() {
        return classes.size() + methods.size() + fields.size();
    }

    /**
     * Get total number of relationships extracted
     */
    public int getTotalRelationships() {
        return relationships.size();
    }

    /**
     * Check if parsing was successful
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
