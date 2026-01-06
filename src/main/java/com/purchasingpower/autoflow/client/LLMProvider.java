package com.purchasingpower.autoflow.client;

import java.util.List;

/**
 * Unified interface for LLM providers (Gemini, Ollama, etc.).
 *
 * Provides chat completion and embedding generation.
 * Implementations handle provider-specific API details.
 *
 * @since 2.0.0
 */
public interface LLMProvider {

    /**
     * Execute chat completion with the LLM.
     *
     * @param prompt The prompt to send
     * @param agentName Name of the calling agent (for metrics)
     * @param conversationId ID of the conversation (for context tracking)
     * @return The LLM's response text
     */
    String chat(String prompt, String agentName, String conversationId);

    /**
     * Generate embedding vector for a single text.
     *
     * @param text Text to embed
     * @return Embedding vector (typically 768 or 1536 dimensions)
     */
    List<Double> embed(String text);

    /**
     * Generate embedding vectors for multiple texts in batch.
     * More efficient than calling embed() multiple times.
     *
     * @param texts List of texts to embed
     * @return List of embedding vectors
     */
    List<List<Double>> embedBatch(List<String> texts);

    /**
     * Get the provider name (for logging/metrics).
     */
    String getProviderName();
}
