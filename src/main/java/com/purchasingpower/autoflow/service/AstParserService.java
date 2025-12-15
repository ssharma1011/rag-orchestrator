package com.purchasingpower.autoflow.service;

import com.purchasingpower.autoflow.model.ast.CodeChunk;

import java.io.File;
import java.util.List;

/**
 * Service for parsing Java source files using AST (Abstract Syntax Tree) analysis.
 * Extracts structured metadata about classes, methods, and their relationships.
 */
public interface AstParserService {

    /**
     * Parses a single Java source file and extracts all code chunks.
     * Returns a hierarchical structure:
     * - 1 parent chunk (class/interface/enum metadata)
     * - N child chunks (methods, constructors)
     *
     * @param javaFile The Java source file to parse
     * @param repoName Repository name for metadata tagging
     * @return List of code chunks (parent + children)
     * @throws IllegalArgumentException if file is not a valid Java file
     */
    List<CodeChunk> parseJavaFile(File javaFile, String repoName);

    /**
     * Parses multiple Java files in batch.
     * More efficient than calling parseJavaFile() in a loop.
     *
     * @param javaFiles List of Java source files
     * @param repoName Repository name for metadata tagging
     * @return List of all code chunks from all files
     */
    List<CodeChunk> parseJavaFiles(List<File> javaFiles, String repoName);

    /**
     * Detects high-level libraries used in a Java file based on imports.
     * Examples: "Spring Framework", "Lombok", "JPA/Hibernate"
     *
     * @param importStatements List of import statements from the file
     * @return List of detected library names
     */
    List<String> detectLibraries(List<String> importStatements);
}