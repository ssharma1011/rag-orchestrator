package com.purchasingpower.autoflow.knowledge;

import com.purchasingpower.autoflow.model.java.JavaClass;
import com.purchasingpower.autoflow.model.java.JavaMethod;

import java.util.List;

/**
 * Service for generating embeddings for Java code elements.
 *
 * Uses enriched text descriptions to create semantic vector embeddings
 * that enable similarity search for code understanding.
 *
 * @since 2.0.0
 */
public interface EmbeddingService {

    /**
     * Generate embedding vector for a Java class.
     *
     * Uses the class's enriched description (from DescriptionGenerator)
     * to create a semantic embedding vector.
     *
     * @param javaClass the class with description already populated
     * @return embedding vector (1024 dimensions for mxbai-embed-large)
     */
    List<Double> generateClassEmbedding(JavaClass javaClass);

    /**
     * Generate embedding vector for a Java method.
     *
     * Uses the method's enriched description (from DescriptionGenerator)
     * to create a semantic embedding vector.
     *
     * @param method the method with description already populated
     * @return embedding vector (1024 dimensions for mxbai-embed-large)
     */
    List<Double> generateMethodEmbedding(JavaMethod method);

    /**
     * Generate embeddings for multiple classes in batch.
     *
     * @param classes list of classes with descriptions
     * @return list of embedding vectors, same order as input
     */
    List<List<Double>> generateClassEmbeddingsBatch(List<JavaClass> classes);

    /**
     * Generate embedding for arbitrary text (useful for search queries).
     *
     * @param text the text to embed
     * @return embedding vector
     */
    List<Double> generateTextEmbedding(String text);
}
