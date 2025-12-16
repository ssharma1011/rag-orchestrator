package com.purchasingpower.autoflow.service.library;

import java.util.List;

/**
 * Contract for services that analyze code context (AST/POM) to identify
 * used libraries and assign specific roles to classes.
 */
public interface LibraryDetectionService {

    /**
     * Detects high-level libraries used in a Java file based on import statements.
     * Examples: ["Spring Framework", "Lombok"]
     * @param importStatements List of FQNs for all imports in a file.
     * @return List of detected library names.
     */
    List<String> detectLibraries(List<String> importStatements);

    /**
     * Calculates specific roles for a class based on its structure.
     * Examples: ["spring-kafka:consumer", "spring-data-jpa:repository"]
     * @param imports The list of fully qualified import statements.
     * @param annotations The list of simple annotation names (e.g., "@Service").
     * @param superInterfaces The list of fully qualified interface names the class implements/extends.
     * @return List of matched roles.
     */
    List<String> detectRoles(List<String> imports, List<String> annotations, List<String> superInterfaces);
}