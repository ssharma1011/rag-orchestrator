package com.purchasingpower.autoflow.knowledge;

import com.purchasingpower.autoflow.model.java.JavaClass;
import java.io.File;
import java.util.List;

/**
 * Service for parsing Java files using JavaParser.
 *
 * Extracts class, method, field, and annotation metadata
 * to build the knowledge graph.
 *
 * @since 2.0.0
 */
public interface JavaParserService {

    /**
     * Parse a single Java file.
     *
     * @param file the Java file to parse
     * @param repositoryId the repository ID
     * @return JavaClass metadata
     */
    JavaClass parseJavaFile(File file, String repositoryId);

    /**
     * Parse multiple Java files.
     *
     * @param files list of Java files
     * @param repositoryId the repository ID
     * @return list of JavaClass metadata
     */
    List<JavaClass> parseJavaFiles(List<File> files, String repositoryId);
}
