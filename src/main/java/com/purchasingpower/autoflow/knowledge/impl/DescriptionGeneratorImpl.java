package com.purchasingpower.autoflow.knowledge.impl;

import com.purchasingpower.autoflow.knowledge.DescriptionGenerator;
import com.purchasingpower.autoflow.model.java.JavaClass;
import com.purchasingpower.autoflow.model.java.JavaMethod;
import com.purchasingpower.autoflow.model.java.JavaParameter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of DescriptionGenerator that creates enriched text descriptions
 * optimized for embedding generation.
 *
 * @since 2.0.0
 */
@Slf4j
@Service
public class DescriptionGeneratorImpl implements DescriptionGenerator {

    @Override
    public String generateClassDescription(JavaClass javaClass) {
        StringBuilder description = new StringBuilder();

        // 1. Class name and inferred purpose
        description.append("Class: ").append(javaClass.getName()).append("\n");
        description.append("Purpose: ").append(inferClassPurpose(javaClass)).append("\n");

        // 2. Package and domain
        if (javaClass.getPackageName() != null && !javaClass.getPackageName().isEmpty()) {
            description.append("Package: ").append(javaClass.getPackageName()).append("\n");
            description.append("Domain: ").append(inferDomain(javaClass.getPackageName())).append("\n");
        }

        // 3. Type kind
        description.append("Type: ").append(javaClass.getKind()).append("\n");

        // 4. Annotations (key Spring/framework annotations)
        if (javaClass.getAnnotations() != null && !javaClass.getAnnotations().isEmpty()) {
            description.append("Annotations: ").append(String.join(", ", javaClass.getAnnotations())).append("\n");
        }

        // 5. Extends/implements
        if (javaClass.getExtendsClass() != null && !javaClass.getExtendsClass().isEmpty()) {
            description.append("Extends: ").append(javaClass.getExtendsClass()).append("\n");
        }
        if (javaClass.getImplementsInterfaces() != null && !javaClass.getImplementsInterfaces().isEmpty()) {
            description.append("Implements: ").append(String.join(", ", javaClass.getImplementsInterfaces())).append("\n");
        }

        // 6. Key methods (names and purposes)
        if (javaClass.getMethods() != null && !javaClass.getMethods().isEmpty()) {
            description.append("Key Methods:\n");
            int methodCount = Math.min(10, javaClass.getMethods().size()); // Limit to 10 methods
            for (int i = 0; i < methodCount; i++) {
                JavaMethod method = javaClass.getMethods().get(i);
                description.append("  - ").append(method.getName());
                if (!method.getAnnotations().isEmpty()) {
                    description.append(" (").append(String.join(", ", method.getAnnotations())).append(")");
                }
                description.append(": ").append(inferMethodPurpose(method)).append("\n");
            }
        }

        // 7. Field types (infer dependencies)
        if (javaClass.getFields() != null && !javaClass.getFields().isEmpty()) {
            List<String> fieldTypes = javaClass.getFields().stream()
                .map(f -> f.getType())
                .distinct()
                .limit(10)
                .collect(Collectors.toList());
            if (!fieldTypes.isEmpty()) {
                description.append("Dependencies: ").append(String.join(", ", fieldTypes)).append("\n");
            }
        }

        // 8. File location
        if (javaClass.getFilePath() != null) {
            description.append("Location: ").append(javaClass.getFilePath()).append("\n");
        }

        log.debug("📝 Generated class description for {} ({} chars)",
            javaClass.getName(), description.length());

        return description.toString();
    }

    @Override
    public String generateMethodDescription(JavaMethod method, JavaClass parentClass) {
        StringBuilder description = new StringBuilder();

        // 1. Method signature and purpose
        description.append("Method: ").append(method.getName()).append("\n");
        description.append("Purpose: ").append(inferMethodPurpose(method)).append("\n");

        // 2. Parent class context
        description.append("Class: ").append(parentClass.getFullyQualifiedName()).append("\n");

        // 3. Annotations
        if (method.getAnnotations() != null && !method.getAnnotations().isEmpty()) {
            description.append("Annotations: ").append(String.join(", ", method.getAnnotations())).append("\n");
        }

        // 4. Parameters
        if (method.getParameters() != null && !method.getParameters().isEmpty()) {
            description.append("Parameters:\n");
            for (JavaParameter param : method.getParameters()) {
                description.append("  - ").append(param.getType()).append(" ").append(param.getName()).append("\n");
            }
        }

        // 5. Return type
        if (method.getReturnType() != null && !method.getReturnType().isEmpty()) {
            description.append("Returns: ").append(method.getReturnType()).append("\n");
        }

        // 6. Method calls (what this method depends on)
        if (method.getMethodCalls() != null && !method.getMethodCalls().isEmpty()) {
            List<String> limitedCalls = method.getMethodCalls().stream()
                .limit(10)
                .collect(Collectors.toList());
            description.append("Calls: ").append(String.join(", ", limitedCalls)).append("\n");
        }

        // 7. Line numbers
        description.append("Lines: ").append(method.getStartLine()).append("-").append(method.getEndLine()).append("\n");

        log.debug("📝 Generated method description for {}.{} ({} chars)",
            parentClass.getName(), method.getName(), description.length());

        return description.toString();
    }

    /**
     * Infer the purpose of a class from its name, annotations, and package.
     */
    private String inferClassPurpose(JavaClass javaClass) {
        // Use annotations to infer purpose
        List<String> annotations = javaClass.getAnnotations();
        if (annotations != null) {
            if (annotations.contains("@RestController") || annotations.contains("@Controller")) {
                return "Handles HTTP requests for REST API endpoints";
            }
            if (annotations.contains("@Service")) {
                return "Business logic service component";
            }
            if (annotations.contains("@Repository")) {
                return "Data access repository for database operations";
            }
            if (annotations.contains("@Configuration")) {
                return "Spring configuration class for bean definitions";
            }
            if (annotations.contains("@Component")) {
                return "Spring-managed component";
            }
            if (annotations.contains("@Entity")) {
                return "JPA entity representing database table";
            }
        }

        // Infer from class name
        String name = javaClass.getName().toLowerCase();
        if (name.endsWith("controller")) {
            return "Handles HTTP requests";
        }
        if (name.endsWith("service") || name.endsWith("serviceimpl")) {
            return "Provides business logic";
        }
        if (name.endsWith("repository") || name.endsWith("dao")) {
            return "Manages data persistence";
        }
        if (name.endsWith("config") || name.endsWith("configuration")) {
            return "Provides application configuration";
        }
        if (name.endsWith("dto") || name.endsWith("request") || name.endsWith("response")) {
            return "Data transfer object";
        }
        if (name.endsWith("entity") || name.endsWith("model")) {
            return "Domain model or data entity";
        }
        if (name.endsWith("exception")) {
            return "Custom exception class";
        }
        if (name.endsWith("util") || name.endsWith("utils") || name.endsWith("helper")) {
            return "Utility or helper class";
        }
        if (name.endsWith("tool")) {
            return "Tool for agent-based operations";
        }

        // Infer from package
        String pkg = javaClass.getPackageName();
        if (pkg != null) {
            if (pkg.contains(".api") || pkg.contains(".controller")) {
                return "API endpoint handler";
            }
            if (pkg.contains(".service")) {
                return "Business logic service";
            }
            if (pkg.contains(".repository") || pkg.contains(".dao")) {
                return "Data access component";
            }
            if (pkg.contains(".model") || pkg.contains(".entity")) {
                return "Data model or entity";
            }
            if (pkg.contains(".config")) {
                return "Configuration component";
            }
        }

        // Default
        return "Java " + javaClass.getKind().name().toLowerCase();
    }

    /**
     * Infer the purpose of a method from its name and annotations.
     */
    private String inferMethodPurpose(JavaMethod method) {
        List<String> annotations = method.getAnnotations();
        if (annotations != null) {
            if (annotations.contains("@GetMapping")) {
                return "HTTP GET endpoint";
            }
            if (annotations.contains("@PostMapping")) {
                return "HTTP POST endpoint";
            }
            if (annotations.contains("@PutMapping")) {
                return "HTTP PUT endpoint";
            }
            if (annotations.contains("@DeleteMapping")) {
                return "HTTP DELETE endpoint";
            }
            if (annotations.contains("@RequestMapping")) {
                return "HTTP request handler";
            }
        }

        String name = method.getName().toLowerCase();
        if (name.startsWith("get")) {
            return "Retrieves " + extractEntity(name.substring(3));
        }
        if (name.startsWith("find")) {
            return "Finds " + extractEntity(name.substring(4));
        }
        if (name.startsWith("search")) {
            return "Searches for " + extractEntity(name.substring(6));
        }
        if (name.startsWith("create") || name.startsWith("add")) {
            return "Creates " + extractEntity(name.substring(6));
        }
        if (name.startsWith("update")) {
            return "Updates " + extractEntity(name.substring(6));
        }
        if (name.startsWith("delete") || name.startsWith("remove")) {
            return "Deletes " + extractEntity(name.substring(6));
        }
        if (name.startsWith("save")) {
            return "Saves " + extractEntity(name.substring(4));
        }
        if (name.startsWith("is") || name.startsWith("has") || name.startsWith("can")) {
            return "Checks condition";
        }
        if (name.startsWith("execute") || name.startsWith("process") || name.startsWith("run")) {
            return "Executes operation";
        }
        if (name.startsWith("build")) {
            return "Builds " + extractEntity(name.substring(5));
        }

        return "Performs " + splitCamelCase(method.getName());
    }

    /**
     * Extract entity name from method name (e.g., "Repository" from "getRepository").
     */
    private String extractEntity(String suffix) {
        if (suffix.isEmpty()) {
            return "data";
        }
        // Split camel case and convert to lowercase
        return splitCamelCase(suffix).toLowerCase();
    }

    /**
     * Split camelCase into words.
     */
    private String splitCamelCase(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    /**
     * Infer domain from package name.
     */
    private String inferDomain(String packageName) {
        if (packageName.contains(".api") || packageName.contains(".controller")) {
            return "API Layer";
        }
        if (packageName.contains(".service")) {
            return "Business Logic Layer";
        }
        if (packageName.contains(".repository") || packageName.contains(".dao")) {
            return "Data Access Layer";
        }
        if (packageName.contains(".model") || packageName.contains(".entity")) {
            return "Domain Model";
        }
        if (packageName.contains(".config")) {
            return "Configuration";
        }
        if (packageName.contains(".util")) {
            return "Utilities";
        }
        if (packageName.contains(".agent")) {
            return "Agent System";
        }
        if (packageName.contains(".knowledge")) {
            return "Knowledge Management";
        }
        if (packageName.contains(".search")) {
            return "Search";
        }

        return "Application";
    }
}
