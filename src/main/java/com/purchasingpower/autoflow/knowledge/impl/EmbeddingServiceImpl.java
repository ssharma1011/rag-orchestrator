package com.purchasingpower.autoflow.knowledge.impl;

import com.purchasingpower.autoflow.client.OllamaClient;
import com.purchasingpower.autoflow.knowledge.EmbeddingService;
import com.purchasingpower.autoflow.model.java.JavaClass;
import com.purchasingpower.autoflow.model.java.JavaMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of EmbeddingService using Ollama mxbai-embed-large.
 *
 * Generates 1024-dimensional embeddings optimized for semantic code search.
 *
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    private final OllamaClient ollamaClient;

    @Override
    public List<Double> generateClassEmbedding(JavaClass javaClass) {
        if (javaClass.getDescription() == null || javaClass.getDescription().isEmpty()) {
            log.warn("⚠️  Class {} has no description, cannot generate embedding", javaClass.getName());
            throw new IllegalArgumentException("Class must have description populated before embedding generation");
        }

        log.debug("🔷 Generating embedding for class: {} (description length: {})",
            javaClass.getName(), javaClass.getDescription().length());

        try {
            List<Double> embedding = ollamaClient.embed(javaClass.getDescription());
            log.debug("✅ Generated embedding for class {} ({} dimensions)",
                javaClass.getName(), embedding.size());
            return embedding;
        } catch (Exception e) {
            log.error("❌ Failed to generate embedding for class {}: {}",
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
            List<Double> embedding = ollamaClient.embed(method.getDescription());
            log.debug("✅ Generated embedding for method {} ({} dimensions)",
                method.getName(), embedding.size());
            return embedding;
        } catch (Exception e) {
            log.error("❌ Failed to generate embedding for method {}: {}",
                method.getName(), e.getMessage());
            throw new RuntimeException("Embedding generation failed for " + method.getName(), e);
        }
    }

    @Override
    public List<List<Double>> generateClassEmbeddingsBatch(List<JavaClass> classes) {
        log.info("🔷 Generating embeddings for {} classes in batch", classes.size());

        List<String> descriptions = classes.stream()
            .map(JavaClass::getDescription)
            .collect(Collectors.toList());

        try {
            List<List<Double>> embeddings = ollamaClient.embedBatch(descriptions);
            log.info("✅ Generated {} embeddings", embeddings.size());
            return embeddings;
        } catch (Exception e) {
            log.error("❌ Batch embedding generation failed: {}", e.getMessage());
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
            List<Double> embedding = ollamaClient.embed(text);
            log.debug("✅ Generated query embedding ({} dimensions)", embedding.size());
            return embedding;
        } catch (Exception e) {
            log.error("❌ Failed to generate embedding for query text: {}", e.getMessage());
            throw new RuntimeException("Query embedding generation failed", e);
        }
    }
}
