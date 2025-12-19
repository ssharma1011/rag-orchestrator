package com.purchasingpower.autoflow.service.library.impl;

import com.purchasingpower.autoflow.configuration.LibraryDetectionProperties;
import com.purchasingpower.autoflow.model.library.DetectionPattern;
import com.purchasingpower.autoflow.model.library.LibraryDefinition;
import com.purchasingpower.autoflow.model.library.LibraryRule;
import com.purchasingpower.autoflow.service.library.LibraryDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Production-grade library detection service.
 * Supports multiple project types (Java, Angular, Hybris) and complex pattern matching.
 */
@Slf4j
@Service
public class LibraryDetectionServiceImpl implements LibraryDetectionService {

    private final List<LibraryDefinition> allLibraries;
    private final Map<String, LibraryDefinition> libraryByName;
    private final Map<String, LibraryDefinition> libraryByPackage;

    public LibraryDetectionServiceImpl(LibraryDetectionProperties properties) {
        this.allLibraries = properties.getLibraries() != null ? properties.getLibraries() : new ArrayList<>();
        
        // Build fast lookup maps
        this.libraryByName = allLibraries.stream()
            .collect(Collectors.toMap(LibraryDefinition::getName, lib -> lib));
        
        this.libraryByPackage = allLibraries.stream()
            .filter(lib -> lib.getBasePackage() != null)
            .collect(Collectors.toMap(LibraryDefinition::getBasePackage, lib -> lib));
        
        log.info("Initialized LibraryDetectionService with {} libraries, {} project types",
                allLibraries.size(),
                allLibraries.stream().map(LibraryDefinition::getProjectType).distinct().count());
    }

    @Override
    public List<String> detectLibraries(List<String> imports) {
        if (imports == null || imports.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> detected = new HashSet<>();

        for (String importStmt : imports) {
            // Check exact package match
            for (LibraryDefinition library : allLibraries) {
                if (library.getBasePackage() != null && importStmt.startsWith(library.getBasePackage())) {
                    detected.add(library.getName());
                }
            }
        }

        return new ArrayList<>(detected);
    }

    @Override
    public List<String> detectRoles(
            List<String> imports,
            List<String> annotations,
            List<String> interfaces,
            String superClass,
            List<String> methodCalls) {

        if (imports == null) imports = Collections.emptyList();
        if (annotations == null) annotations = Collections.emptyList();
        if (interfaces == null) interfaces = Collections.emptyList();
        if (methodCalls == null) methodCalls = Collections.emptyList();

        List<String> detectedRoles = new ArrayList<>();

        // Extract simple class names from imports for efficient lookup
        Set<String> simpleImportedClasses = imports.stream()
                .map(imp -> imp.substring(imp.lastIndexOf('.') + 1))
                .collect(Collectors.toSet());

        // Check each library's rules
        for (LibraryDefinition library : allLibraries) {
            if (library.getRules() == null || library.getRules().isEmpty()) {
                continue;
            }

            for (LibraryRule rule : library.getRules()) {
                if (matchesPattern(rule.getPatterns(), imports, annotations, interfaces,
                        superClass, methodCalls, simpleImportedClasses)) {
                    detectedRoles.add(rule.getRole());
                }
            }
        }

        return detectedRoles;
    }

    @Override
    public LibraryDefinition.ProjectType detectProjectType(List<String> imports) {
        if (imports == null || imports.isEmpty()) {
            return LibraryDefinition.ProjectType.UNKNOWN;
        }

        // Count imports per project type
        Map<LibraryDefinition.ProjectType, Integer> typeScores = new EnumMap<>(LibraryDefinition.ProjectType.class);

        for (String importStmt : imports) {
            for (LibraryDefinition library : allLibraries) {
                if (library.getBasePackage() != null && importStmt.startsWith(library.getBasePackage())) {
                    LibraryDefinition.ProjectType type = library.getProjectType();
                    typeScores.put(type, typeScores.getOrDefault(type, 0) + 1);
                }
            }
        }

        // Return type with highest score
        return typeScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(LibraryDefinition.ProjectType.UNKNOWN);
    }

    @Override
    public boolean hasLibrary(String libraryName, List<String> imports) {
        if (libraryName == null || imports == null) {
            return false;
        }

        LibraryDefinition library = libraryByName.get(libraryName);
        if (library == null) {
            return false;
        }

        return imports.stream()
                .anyMatch(imp -> library.getBasePackage() != null && imp.startsWith(library.getBasePackage()));
    }

    /**
     * Checks if a pattern matches the given code elements.
     */
    private boolean matchesPattern(
            DetectionPattern pattern,
            List<String> imports,
            List<String> annotations,
            List<String> interfaces,
            String superClass,
            List<String> methodCalls,
            Set<String> simpleImportedClasses) {

        if (pattern == null) {
            return false;
        }

        boolean matchMode = pattern.getMode() == DetectionPattern.MatchMode.ALL;
        List<Boolean> checks = new ArrayList<>();

        // Check exact imports
        if (!pattern.getImports().isEmpty()) {
            boolean hasAllImports = imports.containsAll(pattern.getImports());
            checks.add(hasAllImports);
        }

        // Check import prefixes
        if (!pattern.getImportPrefixes().isEmpty()) {
            boolean hasImportPrefix = pattern.getImportPrefixes().stream()
                    .anyMatch(prefix -> imports.stream().anyMatch(imp -> imp.startsWith(prefix)));
            checks.add(hasImportPrefix);
        }

        // Check annotations
        if (!pattern.getAnnotations().isEmpty()) {
            boolean hasAllAnnotations = annotations.containsAll(pattern.getAnnotations());
            checks.add(hasAllAnnotations);
        }

        // Check interfaces
        if (!pattern.getInterfaces().isEmpty()) {
            boolean hasInterface = interfaces.stream()
                    .anyMatch(iface -> pattern.getInterfaces().contains(iface));
            checks.add(hasInterface);
        }

        // Check simple class names in imports
        if (!pattern.getClasses().isEmpty()) {
            boolean hasAllClasses = simpleImportedClasses.containsAll(pattern.getClasses());
            checks.add(hasAllClasses);
        }

        // Check superclass
        if (pattern.getExtendsClass() != null && !pattern.getExtendsClass().isEmpty()) {
            boolean extendsSuperClass = pattern.getExtendsClass().equals(superClass);
            checks.add(extendsSuperClass);
        }

        // Check method calls
        if (!pattern.getMethodCalls().isEmpty()) {
            boolean hasMethodCall = pattern.getMethodCalls().stream()
                    .anyMatch(call -> methodCalls.stream().anyMatch(mc -> mc.contains(call)));
            checks.add(hasMethodCall);
        }

        if (checks.isEmpty()) {
            return false;
        }

        // Apply match mode
        if (matchMode) {
            // ALL mode: every check must pass
            return checks.stream().allMatch(Boolean::booleanValue);
        } else {
            // ANY mode: at least one check must pass
            return checks.stream().anyMatch(Boolean::booleanValue);
        }
    }
}
