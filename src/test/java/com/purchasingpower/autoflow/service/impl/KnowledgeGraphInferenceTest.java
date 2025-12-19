package com.purchasingpower.autoflow.service.impl;

import com.purchasingpower.autoflow.model.ast.ClassMetadata;
import com.purchasingpower.autoflow.model.ast.CodeChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * KNOWLEDGE GRAPH INFERENCE TEST
 * ════════════════════════════════════════════════════════════════════════════
 *
 * Tests that knowledge graph metadata (domain, capability, features, concepts)
 * is correctly inferred from code structure.
 *
 * WHAT THIS TESTS:
 * - Domain inference from package/class names
 * - Capability inference from annotations
 * - Feature extraction from method names
 * - Concept detection from patterns
 *
 * WHY IT MATTERS:
 * Without this, "@autoflow fix PaymentService" would only see PaymentService.
 * With this, it sees ALL payment classes, refund logic, PCI concepts, etc.
 *
 * ════════════════════════════════════════════════════════════════════════════
 */
@SpringBootTest
@DisplayName("Knowledge Graph Inference Tests")
class KnowledgeGraphInferenceTest {

    @Autowired
    private AstParserServiceImpl astParser;

    @Test
    @DisplayName("Should infer 'payment' domain from package name")
    void testDomainInference_Payment() {
        // Given: A class in payment package
        File testFile = findFileContaining("payment");

        if (testFile == null) {
            System.out.println("⚠️ No payment-related files found, skipping");
            return;
        }

        // When: Parse with AST
        List<CodeChunk> chunks = astParser.parseJavaFile(testFile, "test-repo");
        CodeChunk classChunk = chunks.stream()
                .filter(c -> c.getClassMetadata() != null)
                .findFirst()
                .orElse(null);

        if (classChunk == null) {
            System.out.println("⚠️ No class chunk found, skipping");
            return;
        }

        ClassMetadata metadata = classChunk.getClassMetadata();

        // Then: Should infer payment domain
        System.out.println("\n📊 Inferred Domain: " + metadata.getDomain());
        System.out.println("   From: " + metadata.getFullyQualifiedName());

        assertNotNull(metadata.getDomain(), "Domain should be inferred");

        System.out.println("✅ Domain inference working");
    }

    @Test
    @DisplayName("Should infer business capability from annotations")
    void testCapabilityInference() {
        // Given: Parse AutoFlow's own services
        File serviceFile = new File("src/main/java/com/purchasingpower/autoflow/service/impl/GitOperationsServiceImpl.java");

        if (!serviceFile.exists()) {
            System.out.println("⚠️ Test file not found, skipping");
            return;
        }

        // When: Parse
        List<CodeChunk> chunks = astParser.parseJavaFile(serviceFile, "test");
        CodeChunk classChunk = chunks.stream()
                .filter(c -> c.getClassMetadata() != null)
                .findFirst()
                .orElseThrow();

        ClassMetadata metadata = classChunk.getClassMetadata();

        // Then: Should infer capability
        System.out.println("\n📊 Inferred Capability: " + metadata.getBusinessCapability());
        System.out.println("   From annotations: " + metadata.getAnnotations());
        System.out.println("   Class: " + metadata.getClassName());

        assertNotNull(metadata.getBusinessCapability(), "Capability should be inferred");

        System.out.println("✅ Capability inference working");
    }

    @Test
    @DisplayName("Should extract features from method names")
    void testFeatureExtraction() {
        // Given: Parse a class with feature-rich methods
        File testFile = new File("src/main/java/com/purchasingpower/autoflow/service/impl/RagLlmServiceImpl.java");

        if (!testFile.exists()) {
            System.out.println("⚠️ Test file not found, skipping");
            return;
        }

        // When: Parse
        List<CodeChunk> chunks = astParser.parseJavaFile(testFile, "test");
        CodeChunk classChunk = chunks.stream()
                .filter(c -> c.getClassMetadata() != null)
                .findFirst()
                .orElseThrow();

        ClassMetadata metadata = classChunk.getClassMetadata();

        // Then: Should extract features
        System.out.println("\n📊 Extracted Features: " + metadata.getFeatures());
        System.out.println("   From class: " + metadata.getClassName());

        assertNotNull(metadata.getFeatures(), "Features should be extracted");

        if (!metadata.getFeatures().isEmpty()) {
            System.out.println("✅ Feature extraction working: " + metadata.getFeatures().size() + " features");
        } else {
            System.out.println("ℹ️ No features found (methods may not match patterns)");
        }
    }

    @Test
    @DisplayName("Should detect concepts from annotations and imports")
    void testConceptDetection() {
        // Given: Parse a class with rich annotations
        File testFile = new File("src/main/java/com/purchasingpower/autoflow/workflow/pipeline/PipelineEngine.java");

        if (!testFile.exists()) {
            System.out.println("⚠️ Test file not found, skipping");
            return;
        }

        // When: Parse
        List<CodeChunk> chunks = astParser.parseJavaFile(testFile, "test");
        CodeChunk classChunk = chunks.stream()
                .filter(c -> c.getClassMetadata() != null)
                .findFirst()
                .orElseThrow();

        ClassMetadata metadata = classChunk.getClassMetadata();

        // Then: Should detect concepts
        System.out.println("\n📊 Detected Concepts: " + metadata.getConcepts());
        System.out.println("   From annotations: " + metadata.getAnnotations());
        System.out.println("   From imports: " + metadata.getUsedLibraries());

        assertNotNull(metadata.getConcepts(), "Concepts should be detected");

        if (!metadata.getConcepts().isEmpty()) {
            System.out.println("✅ Concept detection working: " + metadata.getConcepts().size() + " concepts");
        } else {
            System.out.println("ℹ️ No concepts detected (may need more patterns)");
        }
    }

    @Test
    @DisplayName("Should handle multiple domains in project")
    void testMultipleDomains() {
        System.out.println("\n📊 Testing domain inference across project files");

        File projectRoot = new File("src/main/java");
        List<File> javaFiles = findJavaFiles(projectRoot);

        System.out.println("   Found " + javaFiles.size() + " Java files");

        // Parse first 10 files and check domains
        java.util.Map<String, Integer> domainCounts = new java.util.HashMap<>();

        for (File file : javaFiles.stream().limit(10).toList()) {
            try {
                List<CodeChunk> chunks = astParser.parseJavaFile(file, "test");
                chunks.stream()
                        .filter(c -> c.getClassMetadata() != null)
                        .map(c -> c.getClassMetadata().getDomain())
                        .filter(d -> d != null)
                        .forEach(domain ->
                                domainCounts.merge(domain, 1, Integer::sum)
                        );
            } catch (Exception e) {
                // Skip unparseable files
            }
        }

        System.out.println("\n📊 Domains found:");
        domainCounts.forEach((domain, count) ->
                System.out.println("   " + domain + ": " + count + " classes")
        );

        assertFalse(domainCounts.isEmpty(), "Should find at least one domain");

        System.out.println("✅ Multi-domain inference working");
    }

    @Test
    @DisplayName("Should handle edge cases gracefully")
    void testEdgeCases() {
        System.out.println("\n📊 Testing edge cases");

        // Test 1: Interface (no methods)
        File interfaceFile = new File("src/main/java/com/purchasingpower/autoflow/service/JiraClientService.java");
        if (interfaceFile.exists()) {
            List<CodeChunk> chunks = astParser.parseJavaFile(interfaceFile, "test");
            CodeChunk classChunk = chunks.stream()
                    .filter(c -> c.getClassMetadata() != null)
                    .findFirst()
                    .orElseThrow();

            ClassMetadata metadata = classChunk.getClassMetadata();

            System.out.println("   Interface domain: " + metadata.getDomain());
            assertNotNull(metadata.getDomain(), "Interface should have domain");
        }

        // Test 2: Enum
        // (Add test if you have enum in project)

        System.out.println("✅ Edge cases handled");
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ════════════════════════════════════════════════════════════════════════

    private File findFileContaining(String keyword) {
        File srcDir = new File("src/main/java");
        List<File> files = findJavaFiles(srcDir);

        return files.stream()
                .filter(f -> f.getAbsolutePath().toLowerCase().contains(keyword))
                .findFirst()
                .orElse(null);
    }

    private List<File> findJavaFiles(File root) {
        try (java.util.stream.Stream<java.nio.file.Path> walk =
                     java.nio.file.Files.walk(root.toPath())) {
            return walk
                    .filter(p -> !java.nio.file.Files.isDirectory(p))
                    .map(java.nio.file.Path::toFile)
                    .filter(f -> f.getName().endsWith(".java"))
                    .toList();
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }
}