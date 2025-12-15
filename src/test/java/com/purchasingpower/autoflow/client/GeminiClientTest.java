package com.purchasingpower.autoflow.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GeminiClient batch embedding functionality.
 *
 * REQUIRES: GEMINI_KEY environment variable
 */
@SpringBootTest
@DisplayName("Gemini Client Tests")
@EnabledIfEnvironmentVariable(named = "GEMINI_KEY", matches = ".+")
class GeminiClientTest {

    @Autowired
    private GeminiClient geminiClient;

    @Test
    @DisplayName("Should create single embedding")
    void testCreateEmbedding_ShouldReturnVector() {
        // Given
        String text = "public class PaymentService { }";

        // When
        List<Double> embedding = geminiClient.createEmbedding(text);

        // Then
        assertNotNull(embedding, "Embedding should not be null");
        assertFalse(embedding.isEmpty(), "Embedding should not be empty");
        assertEquals(768, embedding.size(), "Gemini text-embedding-004 produces 768-dimensional vectors");

        System.out.println("✅ Single embedding: " + embedding.size() + " dimensions");
    }

    @Test
    @DisplayName("Should create batch embeddings")
    void testBatchCreateEmbeddings_ShouldReturnMultipleVectors() {
        // Given
        List<String> texts = List.of(
                "public class UserService { }",
                "public void saveUser(User user) { }",
                "private UserRepository repository;",
                "@Service annotation marks this as a Spring bean"
        );

        // When
        List<List<Double>> embeddings = geminiClient.batchCreateEmbeddings(texts);

        // Then
        assertNotNull(embeddings, "Embeddings should not be null");
        assertEquals(texts.size(), embeddings.size(), "Should return one embedding per input text");

        for (int i = 0; i < embeddings.size(); i++) {
            List<Double> embedding = embeddings.get(i);
            assertNotNull(embedding, "Embedding " + i + " should not be null");
            assertEquals(768, embedding.size(), "Each embedding should be 768-dimensional");
        }

        System.out.println("✅ Batch embeddings: " + embeddings.size() + " vectors created");
    }

    @Test
    @DisplayName("Should handle large batch (100+ items)")
    void testBatchCreateEmbeddings_ShouldHandleLargeBatch() {
        // Given: 150 texts (exceeds single batch limit of 100)
        List<String> texts = new java.util.ArrayList<>();
        for (int i = 0; i < 150; i++) {
            texts.add("Sample text " + i);
        }

        // When
        long startTime = System.currentTimeMillis();
        List<List<Double>> embeddings = geminiClient.batchCreateEmbeddings(texts);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertEquals(150, embeddings.size(), "Should handle batch larger than API limit");

        System.out.println("✅ Large batch (150 items): " + duration + "ms");
        System.out.println("   Average: " + (duration / 150) + "ms per embedding");
    }

    @Test
    @DisplayName("Should handle empty list")
    void testBatchCreateEmbeddings_ShouldHandleEmptyList() {
        // Given
        List<String> emptyList = List.of();

        // When
        List<List<Double>> embeddings = geminiClient.batchCreateEmbeddings(emptyList);

        // Then
        assertNotNull(embeddings, "Should return empty list, not null");
        assertTrue(embeddings.isEmpty(), "Should return empty list for empty input");

        System.out.println("✅ Empty list handling works");
    }

    @Test
    @DisplayName("Should handle null gracefully")
    void testBatchCreateEmbeddings_ShouldHandleNull() {
        // When/Then
        List<List<Double>> embeddings = geminiClient.batchCreateEmbeddings(null);

        assertNotNull(embeddings, "Should return empty list for null input");
        assertTrue(embeddings.isEmpty(), "Should return empty list for null input");

        System.out.println("✅ Null handling works");
    }

    @Test
    @DisplayName("Should produce different embeddings for different texts")
    void testBatchCreateEmbeddings_ShouldProduceDifferentVectors() {
        // Given: Semantically different texts
        List<String> texts = List.of(
                "public class PaymentService implements PaymentProcessor { }",
                "public class UserController extends BaseController { }",
                "SELECT * FROM users WHERE id = ?"
        );

        // When
        List<List<Double>> embeddings = geminiClient.batchCreateEmbeddings(texts);

        // Then: Embeddings should be different
        List<Double> emb1 = embeddings.get(0);
        List<Double> emb2 = embeddings.get(1);
        List<Double> emb3 = embeddings.get(2);

        assertNotEquals(emb1, emb2, "Different texts should produce different embeddings");
        assertNotEquals(emb1, emb3, "Different texts should produce different embeddings");
        assertNotEquals(emb2, emb3, "Different texts should produce different embeddings");

        System.out.println("✅ Different texts produce unique embeddings");
    }

    @Test
    @DisplayName("Should produce similar embeddings for similar texts")
    void testBatchCreateEmbeddings_ShouldProduceSimilarVectors() {
        // Given: Semantically similar texts
        List<String> texts = List.of(
                "public void saveUser(User user) { repository.save(user); }",
                "public void persistUser(User user) { userRepo.save(user); }"
        );

        // When
        List<List<Double>> embeddings = geminiClient.batchCreateEmbeddings(texts);

        // Then: Calculate cosine similarity
        double similarity = cosineSimilarity(embeddings.get(0), embeddings.get(1));

        assertTrue(similarity > 0.8,
                "Similar code should have high similarity (>0.8). Got: " + similarity);

        System.out.println("✅ Similar texts produce similar embeddings");
        System.out.println("   Cosine similarity: " + String.format("%.3f", similarity));
    }

    @Test
    @DisplayName("Backward compatibility: createEmbedding uses batch API internally")
    void testCreateEmbedding_ShouldUseBatchAPIInternally() {
        // Given
        String text = "Sample text for embedding";

        // When: Call old API
        long startTime = System.currentTimeMillis();
        List<Double> embedding = geminiClient.createEmbedding(text);
        long singleDuration = System.currentTimeMillis() - startTime;

        // Then: Should work correctly
        assertNotNull(embedding);
        assertEquals(768, embedding.size());

        // When: Call new batch API with same text
        startTime = System.currentTimeMillis();
        List<List<Double>> batchEmbeddings = geminiClient.batchCreateEmbeddings(List.of(text));
        long batchDuration = System.currentTimeMillis() - startTime;

        // Then: Results should be identical (both use batch API internally)
        assertEquals(embedding, batchEmbeddings.get(0),
                "Single and batch API should produce identical results");

        System.out.println("✅ Backward compatibility maintained");
        System.out.println("   createEmbedding(): " + singleDuration + "ms");
        System.out.println("   batchCreateEmbeddings(1): " + batchDuration + "ms");
    }

    /**
     * Helper: Calculate cosine similarity between two vectors
     */
    private double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1.size() != v2.size()) {
            throw new IllegalArgumentException("Vectors must be same dimension");
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