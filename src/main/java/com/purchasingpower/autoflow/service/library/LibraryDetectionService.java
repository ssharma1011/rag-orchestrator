package com.purchasingpower.autoflow.service.library;

import com.purchasingpower.autoflow.model.library.LibraryDefinition;

import java.util.List;

/**
 * Service for detecting libraries, frameworks, and specific code roles
 * based on static analysis of imports, annotations, and code patterns.
 */
public interface LibraryDetectionService {

    /**
     * Detects high-level libraries used in code based on import statements.
     * 
     * @param imports List of fully qualified import statements
     * @return List of detected library names (e.g., ["Spring Framework", "Lombok"])
     */
    List<String> detectLibraries(List<String> imports);

    /**
     * Detects specific roles/patterns in a class.
     * 
     * @param imports List of fully qualified import statements
     * @param annotations List of annotation simple names (e.g., ["@Service", "@Transactional"])
     * @param interfaces List of fully qualified interface names the class implements
     * @param superClass Fully qualified name of the superclass (can be null)
     * @param methodCalls List of method call patterns found in the class
     * @return List of detected roles (e.g., ["spring-kafka:consumer", "spring:service"])
     */
    List<String> detectRoles(
        List<String> imports,
        List<String> annotations,
        List<String> interfaces,
        String superClass,
        List<String> methodCalls
    );

    /**
     * Detects the primary project type based on file structure and dependencies.
     * 
     * @param imports List of import statements from multiple files
     * @return Detected project type (JAVA_SPRING, ANGULAR, HYBRIS, etc.)
     */
    LibraryDefinition.ProjectType detectProjectType(List<String> imports);

    /**
     * Checks if a specific library is present in the codebase.
     * 
     * @param libraryName Name of the library to check
     * @param imports List of import statements
     * @return true if library is detected
     */
    boolean hasLibrary(String libraryName, List<String> imports);
}
