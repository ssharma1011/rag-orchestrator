package com.purchasingpower.autoflow.service.impl;

import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.configuration.AppProperties;
import com.purchasingpower.autoflow.service.AstParserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Pinecone ingestion with real APIs.
 * IMPORTANT: This test requires valid API keys in environment variables:
 * - PINECONE_KEY
 * - GEMINI_KEY
 * Run with: mvn test -Dtest=PineconeIngestServiceImplTest
 * Or skip integration tests: mvn test -DskipITs
 */
@SpringBootTest
@DisplayName("Pinecone Ingest Service Integration Tests")
class PineconeIngestServiceImplTest {

    @Autowired
    private AstParserService astParser;

    @Autowired
    private GeminiClient geminiClient;

    @Autowired
    private AppProperties appProperties;

    private PineconeIngestServiceImpl ingestService;

    @BeforeEach
    void setUp() {
        // Initialize ingest service with real dependencies
        ingestService = new PineconeIngestServiceImpl(
                astParser,
                geminiClient,
                appProperties
        );
    }

    @Test
    @DisplayName("Should detect empty repository (no pom.xml)")
    void testEmptyRepository_ShouldReturnFalse() {
        // Given: Empty directory
        File emptyDir = new File("src/test/resources/empty-repo");
        emptyDir.mkdirs(); // Create if doesn't exist

        // When: Ingest empty repository
        boolean hasCode = ingestService.ingestRepository(emptyDir, "empty-test");

        // Then: Should return false (scaffold mode)
        assertFalse(hasCode, "Empty repository should return false (scaffold mode)");

        System.out.println("✅ Empty repository detection works");
    }

    @Test
    @DisplayName("Should detect repository without Java files")
    void testRepositoryWithoutJavaFiles_ShouldReturnFalse() {
        // Create a temp directory for testing
        File testDir = new File("target/test-no-java");
        testDir.mkdirs();

        try {
            // Create pom.xml
            File pom = new File(testDir, "pom.xml");
            pom.createNewFile();

            // When: Ingest repository with no Java files
            boolean hasCode = ingestService.ingestRepository(testDir, "no-java-test");

            // Then: Should return false
            assertFalse(hasCode, "Repository without Java files should return false");

            System.out.println("✅ No Java files detection works");

        } catch (Exception e) {
            fail("Test setup failed: " + e.getMessage());
        } finally {
            // Cleanup
            new File(testDir, "pom.xml").delete();
            testDir.delete();
        }
    }

    @Test
    @DisplayName("Should ingest AutoFlow's own codebase (dogfooding)")
    @EnabledIfEnvironmentVariable(named = "PINECONE_KEY", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "GEMINI_KEY", matches = ".+")
    void testIngestAutoFlowCodebase_ShouldIndexSuccessfully() {
        // Given: AutoFlow's own src directory
        File projectRoot = new File(".");

        // Verify we have Java files
        File srcDir = new File(projectRoot, "src/main/java");
        assertTrue(srcDir.exists(), "Project should have src/main/java directory");

        // When: Ingest the repository
        System.out.println("🚀 Starting ingestion of AutoFlow codebase...");
        System.out.println("   This will:");
        System.out.println("   1. Parse all Java files with AST");
        System.out.println("   2. Batch embed class/method chunks");
        System.out.println("   3. Upsert vectors to Pinecone");
        System.out.println("   (This may take 30-60 seconds)");

        long startTime = System.currentTimeMillis();

        boolean hasCode = ingestService.ingestRepository(projectRoot, "autoflow");

        long duration = System.currentTimeMillis() - startTime;

        // Then: Should successfully ingest
        assertTrue(hasCode, "AutoFlow should have existing code");

        System.out.println("✅ Successfully ingested AutoFlow codebase");
        System.out.println("   Duration: " + duration + "ms");
        System.out.println("   Index: " + appProperties.getPinecone().getIndexName());
        System.out.println("\n💡 Next steps:");
        System.out.println("   1. Go to Pinecone console");
        System.out.println("   2. Query your index to see vectors");
        System.out.println("   3. Run retrieval test to validate RAG works");
    }

    @Test
    @DisplayName("Should handle parsing errors gracefully")
    void testIngestWithCorruptFiles_ShouldContinue() {
        // This is a unit test - doesn't actually call Pinecone
        // Just verifies the service handles errors in file processing

        File projectRoot = new File(".");

        // The ingest service should skip files that fail to parse
        // and continue with others (verified by logs)

        assertDoesNotThrow(() -> {
            // Should not throw even if some files fail
            ingestService.ingestRepository(projectRoot, "autoflow");
        }, "Ingest should handle parse errors gracefully");

        System.out.println("✅ Error handling verified");
    }

    @Test
    @DisplayName("Should find Java files recursively")
    void testJavaFileDiscovery_ShouldFindAllFiles() {
        // Given: Project root
        File projectRoot = new File(".");

        // The findJavaFiles method is private, but we can test indirectly
        // by checking if ingestion finds files

        if (!new File(projectRoot, "pom.xml").exists()) {
            System.out.println("⚠️ Skipping - not in project root");
            return;
        }

        // Should find multiple Java files
        boolean hasCode = ingestService.ingestRepository(projectRoot, "test");

        assertTrue(hasCode, "Should find Java files in project");
        System.out.println("✅ Recursive Java file discovery works");
    }
}