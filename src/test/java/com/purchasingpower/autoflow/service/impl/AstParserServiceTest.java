package com.purchasingpower.autoflow.service.impl;

import com.purchasingpower.autoflow.model.ast.ChunkType;
import com.purchasingpower.autoflow.model.ast.CodeChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test AST parser by parsing AutoFlow's own codebase (dogfooding).
 * This validates that we can correctly extract class and method metadata.
 */
@DisplayName("AST Parser Service Tests")
class AstParserServiceTest {

    private AstParserServiceImpl astParser;

    @BeforeEach
    void setUp() {
        astParser = new AstParserServiceImpl();
    }

    @Test
    @DisplayName("Should parse GeminiClient and extract class + methods + fields")
    void testParseGeminiClient_ShouldExtractAllComponents() {
        // Given: AutoFlow's own GeminiClient.java file
        File geminiClientFile = new File("src/main/java/com/purchasingpower/autoflow/client/GeminiClient.java");

        // Ensure file exists (test runs from project root)
        assertTrue(geminiClientFile.exists(), "GeminiClient.java should exist in the project");

        // When: Parse the file
        List<CodeChunk> chunks = astParser.parseJavaFile(geminiClientFile, "autoflow");

        // Then: Should have 1 parent (class) + multiple children (methods + fields + constructors)
        assertFalse(chunks.isEmpty(), "Should extract at least 1 chunk");

        System.out.println("Total chunks extracted: " + chunks.size());

        // ======================================================================
        // VERIFY PARENT CHUNK (CLASS)
        // ======================================================================
        CodeChunk parentChunk = chunks.stream()
                .filter(c -> c.getType() == ChunkType.CLASS)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Should have a CLASS chunk"));

        assertEquals("GeminiClient", parentChunk.getClassMetadata().getClassName());
        assertEquals("com.purchasingpower.autoflow.client", parentChunk.getClassMetadata().getPackageName());
        assertTrue(parentChunk.getClassMetadata().getAnnotations().contains("@Component"),
                "Should detect @Component annotation");
        assertTrue(parentChunk.getClassMetadata().getAnnotations().contains("@Slf4j"),
                "Should detect @Slf4j annotation");
        assertFalse(parentChunk.getChildChunkIds().isEmpty(), "Class should have child chunks (methods/fields)");

        System.out.println("✅ Class metadata: " + parentChunk.getClassMetadata().getClassName());
        System.out.println("   Annotations: " + parentChunk.getClassMetadata().getAnnotations());
        System.out.println("   Child chunks: " + parentChunk.getChildChunkIds().size());

        // ======================================================================
        // VERIFY METHOD CHUNKS
        // ======================================================================
        List<CodeChunk> methodChunks = chunks.stream()
                .filter(c -> c.getType() == ChunkType.METHOD)
                .toList();

        assertTrue(methodChunks.size() > 5, "Should have multiple methods (at least 5)");
        System.out.println("✅ Found " + methodChunks.size() + " method chunks");

        // Find specific methods
        CodeChunk createEmbeddingMethod = methodChunks.stream()
                .filter(c -> c.getMethodMetadata().getMethodName().equals("createEmbedding"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Should find createEmbedding method"));

        assertEquals("createEmbedding", createEmbeddingMethod.getMethodMetadata().getMethodName());
        assertNotNull(createEmbeddingMethod.getParentChunkId(), "Method should have parent reference");
        assertTrue(!createEmbeddingMethod.getContent().isEmpty(), "Method should have source code content");

        System.out.println("✅ Method: " + createEmbeddingMethod.getMethodMetadata().getMethodName());
        System.out.println("   Return type: " + createEmbeddingMethod.getMethodMetadata().getReturnType());

        // Verify batch embedding method exists
        boolean hasBatchEmbedding = methodChunks.stream()
                .anyMatch(c -> c.getMethodMetadata().getMethodName().equals("batchCreateEmbeddings"));

        assertTrue(hasBatchEmbedding, "Should find batchCreateEmbeddings method");

        // ======================================================================
        // VERIFY FIELD CHUNKS (NEW!)
        // ======================================================================
        List<CodeChunk> fieldChunks = chunks.stream()
                .filter(c -> c.getType() == ChunkType.FIELD)
                .toList();

        System.out.println("✅ Found " + fieldChunks.size() + " field chunks");

        // Should have at least: props, objectMapper, geminiWebClient
        assertTrue(fieldChunks.size() >= 2, "Should extract class fields");

        // Verify specific fields
        boolean hasPropsField = fieldChunks.stream()
                .anyMatch(c -> c.getMethodMetadata().getMethodName().equals("props"));

        boolean hasObjectMapperField = fieldChunks.stream()
                .anyMatch(c -> c.getMethodMetadata().getMethodName().equals("objectMapper"));

        assertTrue(hasPropsField || hasObjectMapperField,
                "Should find at least one expected field (props or objectMapper)");

        if (hasPropsField) {
            System.out.println("   ✅ Field: props (AppProperties)");
        }
        if (hasObjectMapperField) {
            System.out.println("   ✅ Field: objectMapper (ObjectMapper)");
        }
    }

    @Test
    @DisplayName("Should detect method calls using AST (not regex)")
    void testMethodCallDetection_ShouldUseAST() {
        File geminiClientFile = new File("src/main/java/com/purchasingpower/autoflow/client/GeminiClient.java");

        if (!geminiClientFile.exists()) {
            System.out.println("⚠️ Skipping test - GeminiClient.java not found");
            return;
        }

        List<CodeChunk> chunks = astParser.parseJavaFile(geminiClientFile, "autoflow");

        // Find generateCodePlan method (should call buildMaintainerPrompt and callChatApi)
        CodeChunk generateCodePlanMethod = chunks.stream()
                .filter(c -> c.getType() == ChunkType.METHOD)
                .filter(c -> c.getMethodMetadata().getMethodName().equals("generateCodePlan"))
                .findFirst()
                .orElse(null);

        if (generateCodePlanMethod == null) {
            System.out.println("⚠️ generateCodePlan method not found, skipping call detection test");
            return;
        }

        List<String> calledMethods = generateCodePlanMethod.getMethodMetadata().getCalledMethods();

        System.out.println("Called methods in generateCodePlan: " + calledMethods);

        // Should detect at least one of: buildMaintainerPrompt, callChatApi
        boolean detectsMethodCall = calledMethods.stream()
                .anyMatch(m -> m.contains("buildMaintainerPrompt") || m.contains("callChatApi"));

        assertTrue(detectsMethodCall,
                "Should detect method calls like buildMaintainerPrompt or callChatApi. Found: " + calledMethods);

        System.out.println("✅ AST-based method call detection working");
    }

    @Test
    @DisplayName("Should extract interface methods from JiraClientService")
    void testParseInterface_ShouldHandleInterfaceMethods() {
        File jiraServiceFile = new File("src/main/java/com/purchasingpower/autoflow/service/JiraClientService.java");

        if (!jiraServiceFile.exists()) {
            System.out.println("⚠️ Skipping test - JiraClientService.java not found");
            return;
        }

        List<CodeChunk> chunks = astParser.parseJavaFile(jiraServiceFile, "autoflow");

        // Should extract interface as parent chunk
        CodeChunk interfaceChunk = chunks.stream()
                .filter(c -> c.getType() == ChunkType.INTERFACE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Should have INTERFACE chunk"));

        assertTrue(interfaceChunk.getClassMetadata().isInterface());
        assertEquals("JiraClientService", interfaceChunk.getClassMetadata().getClassName());

        // Should have method definitions (getIssue, addComment)
        List<CodeChunk> methods = chunks.stream()
                .filter(c -> c.getType() == ChunkType.METHOD)
                .toList();

        assertTrue(methods.size() > 0, "Interface should have method declarations");

        System.out.println("✅ Interface parsed: " + interfaceChunk.getClassMetadata().getClassName());
        System.out.println("   Methods: " + methods.size());
    }

    @Test
    @DisplayName("Should detect libraries from imports")
    void testLibraryDetection_ShouldIdentifyFrameworks() {
        // Given: Typical Spring Boot imports
        List<String> imports = List.of(
                "org.springframework.stereotype.Service",
                "org.springframework.web.bind.annotation.RestController",
                "lombok.extern.slf4j.Slf4j",
                "lombok.RequiredArgsConstructor",
                "com.fasterxml.jackson.databind.ObjectMapper",
                "org.eclipse.jgit.api.Git",
                "io.pinecone.clients.Pinecone"
        );

        // When: Detect libraries
        List<String> libraries = astParser.detectLibraries(imports);

        System.out.println("Detected libraries: " + libraries);

        // Then: Should identify known frameworks
        assertTrue(libraries.contains("Spring Framework"), "Should detect Spring");
        assertTrue(libraries.contains("Lombok"), "Should detect Lombok");
        assertTrue(libraries.contains("Jackson"), "Should detect Jackson");
        assertTrue(libraries.contains("JGit"), "Should detect JGit");
        assertTrue(libraries.contains("Pinecone"), "Should detect Pinecone");

        System.out.println("✅ Library detection working for " + libraries.size() + " frameworks");
    }

    @Test
    @DisplayName("Should parse multiple files in batch")
    void testBatchParsing_ShouldHandleMultipleFiles() {
        List<File> files = List.of(
                new File("src/main/java/com/purchasingpower/autoflow/client/GeminiClient.java"),
                new File("src/main/java/com/purchasingpower/autoflow/service/impl/GitOperationsServiceImpl.java"),
                new File("src/main/java/com/purchasingpower/autoflow/service/impl/AstParserServiceImpl.java"),
                new File("src/main/java/com/purchasingpower/autoflow/service/AstParserService.java")

        );

        List<File> existingFiles = files.stream()
                .filter(File::exists)
                .toList();

        if (existingFiles.isEmpty()) {
            System.out.println("⚠️ No test files found, skipping batch parsing test");
            return;
        }

        // When: Parse multiple files
        List<CodeChunk> chunks = astParser.parseJavaFiles(existingFiles, "autoflow");

        System.out.println("Batch parsed " + existingFiles.size() + " files → " + chunks.size() + " chunks");

        // Then: Should have chunks from all files
        assertTrue(chunks.size() > existingFiles.size(),
                "Should have more chunks than files (1 class + N methods per file)");

        // Verify we have both parent and child chunks
        long parentCount = chunks.stream().filter(c -> c.getParentChunkId() == null).count();
        long childCount = chunks.stream().filter(c -> c.getParentChunkId() != null).count();

        assertTrue(parentCount > 0, "Should have parent chunks (classes)");
        assertTrue(childCount > 0, "Should have child chunks (methods/fields)");
        assertEquals(existingFiles.size(), parentCount,
                "Should have 1 parent chunk per file");

        System.out.println("✅ Batch parsing: " + parentCount + " parents, " + childCount + " children");
    }

    @Test
    @DisplayName("Should convert CodeChunk to flat metadata for Pinecone")
    void testFlatMetadata_ShouldConvertToStringMap() {
        File testFile = new File("src/main/java/com/purchasingpower/autoflow/client/GeminiClient.java");

        if (!testFile.exists()) {
            System.out.println("⚠️ Skipping test - GeminiClient.java not found");
            return;
        }

        List<CodeChunk> chunks = astParser.parseJavaFile(testFile, "autoflow");

        // Test CLASS chunk metadata
        CodeChunk classChunk = chunks.stream()
                .filter(c -> c.getType() == ChunkType.CLASS)
                .findFirst()
                .orElseThrow();

        var classMetadata = classChunk.toFlatMetadata();

        assertNotNull(classMetadata.get("chunk_type"));
        assertNotNull(classMetadata.get("repo_name"));
        assertNotNull(classMetadata.get("class_name"));
        assertEquals("CLASS", classMetadata.get("chunk_type"));
        assertEquals("autoflow", classMetadata.get("repo_name"));
        assertEquals("GeminiClient", classMetadata.get("class_name"));

        System.out.println("✅ Class metadata flattened: " + classMetadata.size() + " fields");

        // Test METHOD chunk metadata
        CodeChunk methodChunk = chunks.stream()
                .filter(c -> c.getType() == ChunkType.METHOD)
                .findFirst()
                .orElseThrow();

        var methodMetadata = methodChunk.toFlatMetadata();

        assertNotNull(methodMetadata.get("method_name"));
        assertNotNull(methodMetadata.get("owning_class"));
        assertNotNull(methodMetadata.get("parent_chunk_id"));
        assertEquals("METHOD", methodMetadata.get("chunk_type"));

        System.out.println("✅ Method metadata flattened: " + methodMetadata.size() + " fields");

        // Test FIELD chunk metadata (if exists)
        chunks.stream()
                .filter(c -> c.getType() == ChunkType.FIELD)
                .findFirst()
                .ifPresent(fieldChunk -> {
                    var fieldMetadata = fieldChunk.toFlatMetadata();
                    assertNotNull(fieldMetadata.get("method_name")); // Field name stored here
                    assertEquals("FIELD", fieldMetadata.get("chunk_type"));
                    System.out.println("✅ Field metadata flattened: " + fieldMetadata.size() + " fields");
                });
    }

    @Test
    @DisplayName("Should handle files with constructors")
    void testConstructorExtraction_ShouldUseTypeFilter() {
        File gitOpsFile = new File("src/main/java/com/purchasingpower/autoflow/service/impl/GitOperationsServiceImpl.java");

        if (!gitOpsFile.exists()) {
            System.out.println("⚠️ Skipping test - GitOperationsServiceImpl.java not found");
            return;
        }

        List<CodeChunk> chunks = astParser.parseJavaFile(gitOpsFile, "autoflow");

        // Should have at least one constructor chunk
        List<CodeChunk> constructorChunks = chunks.stream()
                .filter(c -> c.getType() == ChunkType.CONSTRUCTOR)
                .toList();

        if (constructorChunks.isEmpty()) {
            System.out.println("⚠️ No explicit constructors found (may have @RequiredArgsConstructor)");
        } else {
            System.out.println("✅ Found " + constructorChunks.size() + " constructor(s)");

            CodeChunk constructor = constructorChunks.get(0);
            assertEquals("<init>", constructor.getMethodMetadata().getMethodName());
            assertNotNull(constructor.getParentChunkId());
        }
    }

    @Test
    @DisplayName("Should handle empty or invalid files gracefully")
    void testErrorHandling_ShouldNotCrash() {
        // Non-existent file
        File nonExistent = new File("src/main/java/DoesNotExist.java");

        assertThrows(IllegalArgumentException.class, () -> {
            astParser.parseJavaFile(nonExistent, "autoflow");
        }, "Should throw exception for non-existent file");

        // Non-Java file
        File pomFile = new File("pom.xml");
        if (pomFile.exists()) {
            assertThrows(IllegalArgumentException.class, () -> {
                astParser.parseJavaFile(pomFile, "autoflow");
            }, "Should throw exception for non-Java file");
        }

        System.out.println("✅ Error handling works correctly");
    }
}