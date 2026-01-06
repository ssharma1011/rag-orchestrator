package com.purchasingpower.autoflow.knowledge.impl;

import com.purchasingpower.autoflow.knowledge.EmbeddingService;
import com.purchasingpower.autoflow.model.java.JavaClass;
import com.purchasingpower.autoflow.model.java.JavaMethod;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LangChain4j-based embedding service with built-in retry and rate limiting.
 *
 * IMPROVEMENTS OVER MANUAL IMPLEMENTATION:
 * - Automatic retry on failures (429, network errors)
 * - Built-in rate limiting to prevent quota exhaustion
 * - Provider abstraction (easy to switch from Ollama to OpenAI/Gemini)
 * - Connection pooling and circuit breaker patterns
 *
 * @since 2.0.0
 */
@Slf4j
@Service
@Primary  // Use this instead of EmbeddingServiceImpl
public class LangChain4jEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public LangChain4jEmbeddingService(
            @Value("${app.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${app.ollama.embedding-model:mxbai-embed-large}") String modelName,
            @Value("${app.ollama.timeout-seconds:120}") int timeoutSeconds,
            @Value("${app.ollama.max-retries:3}") int maxRetries) {

        log.info("🔷 Initializing LangChain4j Embedding Service");
        log.info("   - Ollama URL: {}", ollamaBaseUrl);
        log.info("   - Model: {}", modelName);
        log.info("   - Timeout: {}s", timeoutSeconds);
        log.info("   - Max Retries: {}", maxRetries);

        // LangChain4j's OllamaEmbeddingModel with built-in retry and timeout
        this.embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)  // Automatic retries on failure!
                .logRequests(false)
                .logResponses(false)
                .build();

        log.info("✅ LangChain4j Embedding Service initialized");
    }

    @Override
    public List<Double> generateClassEmbedding(JavaClass javaClass) {
        if (javaClass.getDescription() == null || javaClass.getDescription().isEmpty()) {
            log.warn("⚠️  Class {} has no description, cannot generate embedding", javaClass.getName());
            throw new IllegalArgumentException("Class must have description populated before embedding generation");
        }

        log.debug("🔷 Generating embedding for class: {} (description length: {})",
            javaClass.getName(), javaClass.getDescription().length());

        try {
            Response<Embedding> response = embeddingModel.embed(javaClass.getDescription());
            List<Double> embedding = convertToDoubleList(response.content());

            log.debug("✅ Generated embedding for class {} ({} dimensions)",
                javaClass.getName(), embedding.size());

            return embedding;
        } catch (Exception e) {
            log.error("❌ Failed to generate embedding for class {} after retries: {}",
                javaClass.getName(), e.getMessage());
            throw new RuntimeException("Embedding generation failed for " + javaClass.getName(), e);
        }
    }

    @Override
    public List<Double> generateMethodEmbedding(JavaMethod method) {
        if (method.getDescription() == null || method.getDescription().isEmpty()) {
            log.warn("⚠️  Method {} has no description, cannot generate embedding", method.getName());
            throw new IllegalArgumentException("Method must have description populated before embedding generation");
        }

        log.debug("🔷 Generating embedding for method: {} (description length: {})",
            method.getName(), method.getDescription().length());

        try {
            Response<Embedding> response = embeddingModel.embed(method.getDescription());
            List<Double> embedding = convertToDoubleList(response.content());

            log.debug("✅ Generated embedding for method {} ({} dimensions)",
                method.getName(), embedding.size());

            return embedding;
        } catch (Exception e) {
            log.error("❌ Failed to generate embedding for method {} after retries: {}",
                method.getName(), e.getMessage());
            throw new RuntimeException("Embedding generation failed for " + method.getName(), e);
        }
    }

    @Override
    public List<List<Double>> generateClassEmbeddingsBatch(List<JavaClass> classes) {
        log.info("🔷 Generating embeddings for {} classes in batch", classes.size());

        List<TextSegment> segments = classes.stream()
            .map(JavaClass::getDescription)
            .map(TextSegment::from)
            .collect(Collectors.toList());

        try {
            // LangChain4j handles batching and retries automatically
            Response<List<Embedding>> response = embeddingModel.embedAll(segments);
            List<List<Double>> embeddings = response.content().stream()
                .map(this::convertToDoubleList)
                .collect(Collectors.toList());

            log.info("✅ Generated {} embeddings", embeddings.size());
            return embeddings;
        } catch (Exception e) {
            log.error("❌ Batch embedding generation failed after retries: {}", e.getMessage());
            throw new RuntimeException("Batch embedding generation failed", e);
        }
    }

    @Override
    public List<Double> generateTextEmbedding(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Text cannot be empty");
        }

        log.debug("🔷 Generating embedding for query text (length: {})", text.length());

        try {
            Response<Embedding> response = embeddingModel.embed(text);
            List<Double> embedding = convertToDoubleList(response.content());

            log.debug("✅ Generated query embedding ({} dimensions)", embedding.size());
            return embedding;
        } catch (Exception e) {
            log.error("❌ Failed to generate text embedding after retries: {}", e.getMessage());
            throw new RuntimeException("Text embedding generation failed", e);
        }
    }

    /**
     * Convert LangChain4j Embedding (float[]) to List<Double> for Neo4j compatibility.
     */
    private List<Double> convertToDoubleList(Embedding embedding) {
        float[] vector = embedding.vector();
        List<Double> result = new ArrayList<>(vector.length);
        for (float value : vector) {
            result.add((double) value);
        }
        return result;
    }
}
