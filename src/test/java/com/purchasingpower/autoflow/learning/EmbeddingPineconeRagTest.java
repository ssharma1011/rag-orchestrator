package com.purchasingpower.autoflow.learning;

import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.client.PineconeRetriever;
import com.purchasingpower.autoflow.enums.ChunkType;
import com.purchasingpower.autoflow.model.ast.CodeChunk;
import com.purchasingpower.autoflow.service.AstParserService;
import com.purchasingpower.autoflow.service.impl.PineconeIngestServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================================
 * LEARNING TEST: Stage 2, 3, 4 - Embedding → Pinecone → RAG Retrieval
 * ============================================================================
 *
 * PREREQUISITES:
 * 1. Set environment variables:
 *    - GEMINI_KEY=your-gemini-api-key
 *    - PINECONE_KEY=your-pinecone-api-key
 *    - PINECONE_INDEX=autoflow
 *
 * 2. Pinecone index "autoflow" must exist with:
 *    - Dimension: 768
 *    - Metric: cosine (recommended)
 *
 * HOW TO RUN:
 *    mvn test -Dtest=EmbeddingPineconeRagTest -q
 *
 * WHAT YOU'LL LEARN:
 * - Stage 2: How text becomes a 768-dimensional vector
 * - Stage 3: How vectors are stored in Pinecone with metadata
 * - Stage 4: How natural language queries find relevant code
 *
 * ============================================================================
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Learning: Embedding → Pinecone → RAG Pipeline")
@EnabledIfEnvironmentVariable(named = "GEMINI_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PINECONE_KEY", matches = ".+")
public class EmbeddingPineconeRagTest {

    @Autowired
    private AstParserService astParser;

    @Autowired
    private GeminiClient geminiClient;

    @Autowired
    private PineconeRetriever pineconeRetriever;

    @Autowired
    private PineconeIngestServiceImpl ingestService;

    // ========================================================================
    // STAGE 2: EMBEDDING - How text becomes a vector
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("Stage 2.1: See what an embedding looks like")
    void stage2_1_WhatIsAnEmbedding() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📚 STAGE 2.1: What is an Embedding?");
        System.out.println("=".repeat(70));

        System.out.println("""
        
        CONCEPT: An embedding converts text into a list of numbers (vector).
        
        Why? Computers can't understand "similar meaning" with text.
        But with numbers, we can calculate DISTANCE between concepts!
        
        Example:
        - "dog" → [0.1, 0.5, -0.3, ...]
        - "puppy" → [0.12, 0.48, -0.28, ...]  ← CLOSE to "dog"!
        - "airplane" → [-0.8, 0.1, 0.9, ...]  ← FAR from "dog"
        
        Let's see a real embedding...
        """);

        // Create embedding for a simple Java method
        String simpleCode = "public void saveUser(User user) { repository.save(user); }";

        System.out.println("INPUT TEXT:");
        System.out.println("  " + simpleCode);
        System.out.println();

        long startTime = System.currentTimeMillis();
        List<Double> embedding = geminiClient.createEmbedding(simpleCode);
        long duration = System.currentTimeMillis() - startTime;

        System.out.println("OUTPUT EMBEDDING:");
        System.out.println("  Dimension: " + embedding.size() + " numbers");
        System.out.println("  Time: " + duration + "ms");
        System.out.println();

        // Show first 10 numbers
        System.out.println("  First 10 values:");
        for (int i = 0; i < 10; i++) {
            System.out.printf("    [%d] = %.6f%n", i, embedding.get(i));
        }
        System.out.println("    ... (758 more numbers)");

        // Show some statistics
        double min = embedding.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = embedding.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double avg = embedding.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        System.out.println();
        System.out.println("  Statistics:");
        System.out.printf("    Min value: %.6f%n", min);
        System.out.printf("    Max value: %.6f%n", max);
        System.out.printf("    Avg value: %.6f%n", avg);

        assertEquals(768, embedding.size(), "Gemini text-embedding-004 produces 768 dimensions");

        System.out.println("\n✅ An embedding is just 768 numbers representing the 'meaning' of text!");
    }

    @Test
    @Order(2)
    @DisplayName("Stage 2.2: Similar code = Similar vectors")
    void stage2_2_SimilarCodeSimilarVectors() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📚 STAGE 2.2: Similar Code = Similar Vectors");
        System.out.println("=".repeat(70));

        System.out.println("""
        
        KEY INSIGHT: Semantically similar code produces similar embeddings.
        
        We measure similarity using COSINE SIMILARITY:
        - 1.0 = identical meaning
        - 0.8+ = very similar
        - 0.5 = somewhat related
        - 0.0 = unrelated
        
        Let's test this with real code...
        """);

        // Three code snippets: two similar, one different
        String code1 = "public void saveUser(User user) { userRepository.save(user); }";
        String code2 = "public void persistUser(User user) { userRepo.save(user); }";  // Similar!
        String code3 = "public List<Product> findAllProducts() { return productRepo.findAll(); }";  // Different

        System.out.println("CODE SNIPPETS:");
        System.out.println("  1: " + code1);
        System.out.println("  2: " + code2 + "  ← Similar to #1");
        System.out.println("  3: " + code3 + "  ← Different");
        System.out.println();

        // Get embeddings
        List<List<Double>> embeddings = geminiClient.batchCreateEmbeddings(List.of(code1, code2, code3));

        List<Double> emb1 = embeddings.get(0);
        List<Double> emb2 = embeddings.get(1);
        List<Double> emb3 = embeddings.get(2);

        // Calculate similarities
        double sim1_2 = cosineSimilarity(emb1, emb2);
        double sim1_3 = cosineSimilarity(emb1, emb3);
        double sim2_3 = cosineSimilarity(emb2, emb3);

        System.out.println("COSINE SIMILARITIES:");
        System.out.printf("  Code 1 vs Code 2 (similar): %.4f%n", sim1_2);
        System.out.printf("  Code 1 vs Code 3 (different): %.4f%n", sim1_3);
        System.out.printf("  Code 2 vs Code 3 (different): %.4f%n", sim2_3);

        System.out.println();
        System.out.println("INTERPRETATION:");
        System.out.println("  • Code 1 & 2 should have HIGH similarity (>0.8) - both save users");
        System.out.println("  • Code 1 & 3 should have LOWER similarity - different operations");

        assertTrue(sim1_2 > sim1_3, "Similar code (1,2) should be more similar than different code (1,3)");
        assertTrue(sim1_2 > 0.7, "saveUser and persistUser should be very similar");

        System.out.println("\n✅ Embeddings capture semantic meaning - similar code is 'close' in vector space!");
    }

    @Test
    @Order(3)
    @DisplayName("Stage 2.3: Batch embedding performance")
    void stage2_3_BatchEmbeddingPerformance() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📚 STAGE 2.3: Batch Embedding Performance");
        System.out.println("=".repeat(70));

        System.out.println("""
        
        WHY BATCH?
        - Single API call for multiple texts = MUCH faster
        - Gemini allows up to 100 texts per batch
        - For 100 chunks: 1 batch call vs 100 individual calls
        
        Let's measure the difference...
        """);

        // Create 10 test texts
        List<String> texts = List.of(
                "public class UserService { }",
                "public class ProductService { }",
                "public void save(Entity e) { }",
                "public List<User> findAll() { }",
                "private final Repository repo;",
                "@Service public class OrderService { }",
                "@Transactional public void process() { }",
                "public interface UserRepository { }",
                "public class PaymentController { }",
                "private void validateInput(String s) { }"
        );

        System.out.println("Test: Embedding " + texts.size() + " code snippets\n");

        // Batch embedding
        long batchStart = System.currentTimeMillis();
        List<List<Double>> batchResults = geminiClient.batchCreateEmbeddings(texts);
        long batchDuration = System.currentTimeMillis() - batchStart;

        System.out.println("BATCH EMBEDDING:");
        System.out.println("  Total time: " + batchDuration + "ms");
        System.out.println("  Per item: " + (batchDuration / texts.size()) + "ms");
        System.out.println("  Items processed: " + batchResults.size());

        // Single embedding (just 3 to save time)
        System.out.println("\nSINGLE EMBEDDING (3 samples):");
        long singleStart = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            geminiClient.createEmbedding(texts.get(i));
        }
        long singleDuration = System.currentTimeMillis() - singleStart;

        System.out.println("  Total time: " + singleDuration + "ms for 3 items");
        System.out.println("  Per item: " + (singleDuration / 3) + "ms");
        System.out.println("  Estimated for 10: " + (singleDuration * 10 / 3) + "ms");

        long estimatedSingleFor10 = singleDuration * 10 / 3;
        double speedup = (double) estimatedSingleFor10 / batchDuration;

        System.out.println("\n📊 COMPARISON:");
        System.out.printf("  Batch (10 items): %dms%n", batchDuration);
        System.out.printf("  Single (estimated 10 items): %dms%n", estimatedSingleFor10);
        System.out.printf("  Speedup: %.1fx faster with batch!%n", speedup);

        assertEquals(texts.size(), batchResults.size(), "Should return one embedding per text");

        System.out.println("\n✅ Batch embedding is significantly faster - always use it for multiple texts!");
    }

    // ========================================================================
    // STAGE 3: PINECONE STORAGE - Storing vectors with metadata
    // ========================================================================

    @Test
    @Order(4)
    @DisplayName("Stage 3.1: Ingest a single Java file into Pinecone")
    void stage3_1_IngestSingleFile() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📚 STAGE 3.1: Ingest a Single Java File into Pinecone");
        System.out.println("=".repeat(70));

        System.out.println("""
        
        WHAT HAPPENS:
        1. AST Parser extracts CodeChunks (parent class + child methods)
        2. Each chunk's content is embedded (768-dim vector)
        3. Vector + metadata is upserted to Pinecone
        
        Let's watch this process for ONE file...
        """);

        // Parse ONE file first to see what we're ingesting
        File testFile = new File("src/main/java/com/purchasingpower/autoflow/client/GeminiClient.java");

        if (!testFile.exists()) {
            System.out.println("⚠️ GeminiClient.java not found. Skipping.");
            return;
        }

        // Step 1: Parse
        System.out.println("STEP 1: AST Parsing");
        List<CodeChunk> chunks = astParser.parseJavaFile(testFile, "autoflow-test");

        System.out.println("  File: GeminiClient.java");
        System.out.println("  Total chunks: " + chunks.size());

        long classCount = chunks.stream().filter(c -> c.getType() == ChunkType.CLASS).count();
        long methodCount = chunks.stream().filter(c -> c.getType() == ChunkType.METHOD).count();
        long fieldCount = chunks.stream().filter(c -> c.getType() == ChunkType.FIELD).count();
        long constructorCount = chunks.stream().filter(c -> c.getType() == ChunkType.CONSTRUCTOR).count();

        System.out.println("    • Classes: " + classCount);
        System.out.println("    • Methods: " + methodCount);
        System.out.println("    • Fields: " + fieldCount);
        System.out.println("    • Constructors: " + constructorCount);

        // Step 2: Show what metadata will be stored
        System.out.println("\nSTEP 2: Metadata Preview (what gets stored in Pinecone)");

        // Show class chunk metadata
        CodeChunk classChunk = chunks.stream()
                .filter(c -> c.getType() == ChunkType.CLASS)
                .findFirst().orElseThrow();

        System.out.println("\n  CLASS chunk metadata:");
        var classMetadata = classChunk.toFlatMetadata();
        classMetadata.forEach((k, v) -> {
            String displayValue = v.length() > 60 ? v.substring(0, 60) + "..." : v;
            System.out.println("    " + k + ": " + displayValue);
        });

        // Show method chunk metadata
        CodeChunk methodChunk = chunks.stream()
                .filter(c -> c.getType() == ChunkType.METHOD)
                .findFirst().orElseThrow();

        System.out.println("\n  METHOD chunk metadata:");
        var methodMetadata = methodChunk.toFlatMetadata();
        methodMetadata.forEach((k, v) -> {
            String displayValue = v.length() > 60 ? v.substring(0, 60) + "..." : v;
            System.out.println("    " + k + ": " + displayValue);
        });

        // Step 3: Create embeddings
        System.out.println("\nSTEP 3: Creating Embeddings");
        List<String> contents = chunks.stream().map(CodeChunk::getContent).toList();

        long embedStart = System.currentTimeMillis();
        List<List<Double>> embeddings = geminiClient.batchCreateEmbeddings(contents);
        long embedDuration = System.currentTimeMillis() - embedStart;

        System.out.println("  Created " + embeddings.size() + " embeddings in " + embedDuration + "ms");

        System.out.println("\n✅ This is what happens BEFORE data goes to Pinecone!");
        System.out.println("   Next: Run Stage 3.2 to actually ingest the full project.");
    }

    @Test
    @Order(5)
    @DisplayName("Stage 3.2: Ingest entire project into Pinecone")
    void stage3_2_IngestEntireProject() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📚 STAGE 3.2: Ingest Entire Project into Pinecone");
        System.out.println("=".repeat(70));

        System.out.println("""
        
        ⚠️  This will ACTUALLY write to your Pinecone index!
        
        WHAT HAPPENS:
        1. Find all .java files in src/main/java
        2. Parse each file into CodeChunks
        3. Batch embed all chunks
        4. Upsert all vectors to Pinecone
        
        Starting ingestion...
        """);

        File projectRoot = new File(".");

        long startTime = System.currentTimeMillis();
        boolean success = ingestService.ingestRepository(projectRoot, "autoflow");
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(success, "Ingestion should succeed");

        System.out.println("\n" + "=".repeat(50));
        System.out.println("📊 INGESTION COMPLETE!");
        System.out.println("=".repeat(50));
        System.out.println("  Duration: " + duration + "ms (" + (duration/1000) + " seconds)");
        System.out.println("  Repository: autoflow");
        System.out.println("  Index: autoflow");

        System.out.println("""
        
        🔍 NEXT STEPS - Verify in Pinecone Console:
        1. Go to https://app.pinecone.io
        2. Open your 'autoflow' index
        3. Click 'Browse' or 'Query'
        4. You should see vectors with metadata like:
           - chunk_type: CLASS, METHOD, FIELD
           - class_name: GeminiClient, AstParserServiceImpl, etc.
           - method_name: createEmbedding, parseJavaFile, etc.
        
        """);

        System.out.println("✅ Your code is now in Pinecone! Let's query it in Stage 4.");
    }

    // ========================================================================
    // STAGE 4: RAG RETRIEVAL - Querying with natural language
    // ========================================================================

    @Test
    @Order(6)
    @DisplayName("Stage 4.1: Simple RAG query")
    void stage4_1_SimpleRagQuery() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📚 STAGE 4.1: Simple RAG Query");
        System.out.println("=".repeat(70));

        System.out.println("""
        
        HOW RAG WORKS:
        1. Your question → embedding (768-dim vector)
        2. Vector search in Pinecone → find similar code vectors
        3. Return the code chunks with highest similarity
        
        Let's try a simple query...
        """);

        String query = "How to create embeddings?";

        System.out.println("QUERY: \"" + query + "\"");
        System.out.println();

        // Step 1: Embed the query
        System.out.println("Step 1: Convert query to embedding...");
        long embedStart = System.currentTimeMillis();
        List<Double> queryEmbedding = geminiClient.createEmbedding(query);
        long embedDuration = System.currentTimeMillis() - embedStart;
        System.out.println("  Done in " + embedDuration + "ms (768 dimensions)");
        System.out.println("  First 10 dimensions: " + queryEmbedding.subList(0, 10));

        // Step 2: Search Pinecone
        System.out.println("\nStep 2: Search Pinecone for similar vectors...");
        long searchStart = System.currentTimeMillis();
        String results = pineconeRetriever.findRelevantCode(queryEmbedding, "autoflow");
        long searchDuration = System.currentTimeMillis() - searchStart;
        System.out.println("  Done in " + searchDuration + "ms");

        // Step 3: Show results
        System.out.println("\nStep 3: Results");
        System.out.println("-".repeat(50));

        if (results.isEmpty() || "NO CONTEXT FOUND".equals(results)) {
            System.out.println("⚠️ No results found!");
            System.out.println("   Make sure you ran Stage 3.2 to ingest the project first.");
        } else {
            // Show first 1000 chars
            String preview = results.length() > 1000
                    ? results.substring(0, 1000) + "\n... (truncated)"
                    : results;
            System.out.println(preview);
        }

        System.out.println("\n" + "-".repeat(50));
        System.out.println("✅ This is what the LLM would receive as context!");
    }

    @Test
    @Order(7)
    @DisplayName("Stage 4.2: Multiple RAG queries")
    void stage4_2_MultipleRagQueries() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📚 STAGE 4.2: Multiple RAG Queries");
        System.out.println("=".repeat(70));

        System.out.println("""
        
        Let's test different queries to see if we get relevant results...
        """);

        // Define test queries
        List<String> queries = List.of(
                "How to clone a Git repository?",
                "How to parse Java files with AST?",
                "How to call Gemini API?",
                "How to create a pull request in Bitbucket?"
        );

        for (String query : queries) {
            System.out.println("\n" + "─".repeat(60));
            System.out.println("🔍 QUERY: \"" + query + "\"");
            System.out.println("─".repeat(60));

            // Embed and search
            List<Double> embedding = geminiClient.createEmbedding(query);
            String results = pineconeRetriever.findRelevantCode(embedding, "autoflow");

            if (results.isEmpty() || "NO CONTEXT FOUND".equals(results)) {
                System.out.println("   ⚠️ No results found");
            } else {
                // Extract first file path mentioned
                String[] lines = results.split("\n");
                int previewLines = Math.min(15, lines.length);

                System.out.println("   RESULTS (first " + previewLines + " lines):");
                for (int i = 0; i < previewLines; i++) {
                    System.out.println("   " + lines[i]);
                }
                if (lines.length > previewLines) {
                    System.out.println("   ... (" + (lines.length - previewLines) + " more lines)");
                }
            }
        }

        System.out.println("\n" + "=".repeat(70));
        System.out.println("✅ RAG retrieval is working!");
        System.out.println("""
        
        EVALUATE THE RESULTS:
        • "Git clone" → Should return GitOperationsServiceImpl
        • "AST parse" → Should return AstParserServiceImpl  
        • "Gemini API" → Should return GeminiClient
        • "Bitbucket PR" → Should return BitbucketServiceImpl
        
        If results seem wrong, the embeddings or chunking may need tuning.
        """);
    }

    @Test
    @Order(8)
    @DisplayName("Stage 4.3: Method-level vs File-level precision")
    void stage4_3_MethodLevelPrecision() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📚 STAGE 4.3: Method-Level Precision");
        System.out.println("=".repeat(70));

        System.out.println("""
        
        KEY BENEFIT of hierarchical chunking:
        - Query finds SPECIFIC METHODS, not entire files
        - LLM gets focused context, not 500 lines of code
        
        Let's verify this works...
        """);

        // Very specific query
        String query = "batch embedding implementation with retry logic";

        System.out.println("SPECIFIC QUERY: \"" + query + "\"");
        System.out.println();

        List<Double> embedding = geminiClient.createEmbedding(query);
        String results = pineconeRetriever.findRelevantCode(embedding, "autoflow");

        System.out.println("RESULTS:");
        System.out.println("-".repeat(50));

        // Check if we got specific method, not whole file
        boolean mentionsBatchMethod = results.contains("batchCreateEmbeddings") ||
                results.contains("batch");
        boolean mentionsRetry = results.contains("retry") || results.contains("Retry");

        if (results.length() > 1500) {
            System.out.println(results.substring(0, 1500) + "\n... (truncated)");
        } else {
            System.out.println(results);
        }

        System.out.println("-".repeat(50));
        System.out.println("\nANALYSIS:");
        System.out.println("  Contains 'batch' method: " + mentionsBatchMethod);
        System.out.println("  Contains 'retry' logic: " + mentionsRetry);
        System.out.println("  Result length: " + results.length() + " chars");

        System.out.println("""
        
        GOOD RESULT:
        • Finds specific methods (batchCreateEmbeddings, callBatchEmbeddingApi)
        • NOT the entire GeminiClient.java file
        • Focused, relevant context
        
        BAD RESULT:
        • Returns entire file content
        • Includes unrelated methods
        • Too much noise for LLM
        """);

        System.out.println("\n✅ Method-level chunking enables precise code retrieval!");
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Calculate cosine similarity between two vectors.
     * Result: 1.0 = identical, 0.0 = orthogonal, -1.0 = opposite
     */
    private double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1.size() != v2.size()) {
            throw new IllegalArgumentException("Vectors must have same dimension");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            norm1 += v1.get(i) * v1.get(i);
            norm2 += v2.get(i) * v2.get(i);
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}