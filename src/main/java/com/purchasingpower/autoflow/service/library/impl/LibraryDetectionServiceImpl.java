package com.purchasingpower.autoflow.service.library.impl;


import com.purchasingpower.autoflow.library.LibraryRuleProperties;
import com.purchasingpower.autoflow.service.library.LibraryDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of LibraryDetectionService.
 * Reads rules from configured LibraryRuleProperties (library-rules.yml).
 */
@Slf4j
@Service
public class LibraryDetectionServiceImpl implements LibraryDetectionService { // <--- CORRECTED PACKAGE AND NAME

    private final List<LibraryConfig> allLibraries;

    public LibraryDetectionServiceImpl(LibraryRuleProperties properties) {
        this.allLibraries = properties.getLibraries() != null ? properties.getLibraries() : List.of();
        log.info("Initialized LibraryDetectionServiceImpl with {} framework definitions.", this.allLibraries.size());
    }

    @Override
    public List<String> detectLibraries(List<String> importStatements) {
        Set<String> detected = new HashSet<>();
        for (String imp : importStatements) {
            for (LibraryConfig library : allLibraries) {
                // General detection based on base package prefix
                if (imp.startsWith(library.getBasePackage())) {
                    detected.add(library.getName());
                }
            }
        }
        return new ArrayList<>(detected);
    }

    @Override
    public List<String> detectRoles(List<String> imports, List<String> annotations, List<String> superInterfaces) {
        List<String> detectedRoles = new ArrayList<>();

        // Helper set for efficient simple class name lookup (for patterns.classes matching)
        Set<String> simpleImportedClasses = imports.stream()
                .map(s -> s.substring(s.lastIndexOf('.') + 1))
                .collect(Collectors.toSet());

        for (LibraryConfig library : allLibraries) {
            if (library.getRules() == null) continue;
            for (RuleConfig rule : library.getRules()) {
                DetectionPatterns patterns = rule.getPatterns();
                boolean matches = true;

                // 1. Check required imports (FQNs)
                if (!patterns.getImports().isEmpty() && !imports.containsAll(patterns.getImports())) {
                    matches = false;
                }

                // 2. Check required annotations (Simple Names - e.g., "@Service")
                if (!patterns.getAnnotations().isEmpty() && !annotations.containsAll(patterns.getAnnotations())) {
                    matches = false;
                }

                // 3. Check required interfaces (FQNs)
                if (!patterns.getInterfaces().isEmpty() && !superInterfaces.containsAll(patterns.getInterfaces())) {
                    matches = false;
                }

                // 4. Check required classes (Simple Names - checks if a FQN import contains the class name)
                if (!patterns.getClasses().isEmpty()) {
                    if (!simpleImportedClasses.containsAll(patterns.getClasses())) {
                        matches = false;
                    }
                }

                if (matches) {
                    detectedRoles.add(rule.getRole());
                }
            }
        }
        return detectedRoles;
    }
}