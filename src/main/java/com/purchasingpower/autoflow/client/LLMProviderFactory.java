package com.purchasingpower.autoflow.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Factory for selecting the active LLM provider.
 *
 * Routes to Ollama (local) or Gemini (cloud) based on configuration.
 * Enables easy switching between providers without code changes.
 *
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LLMProviderFactory {

    private final GeminiProvider geminiProvider;
    private final OllamaClient ollamaProvider;

    @Value("${app.llm-provider:ollama}")
    private String providerName;

    @PostConstruct
    public void init() {
        log.info("🚀 LLM Provider configured: {}", providerName);
        log.info("   Active provider: {}", getProvider().getProviderName());
    }

    /**
     * Get the active LLM provider based on configuration.
     */
    public LLMProvider getProvider() {
        return switch (providerName.toLowerCase()) {
            case "ollama" -> ollamaProvider;
            case "gemini" -> geminiProvider;
            case "hybrid" -> geminiProvider; // Use Gemini for chat in hybrid mode
            default -> {
                log.warn("Unknown LLM provider: {}, falling back to Ollama", providerName);
                yield ollamaProvider;
            }
        };
    }

    /**
     * Get provider for embeddings specifically.
     * In hybrid mode, always use Ollama for embeddings (one-time indexing cost).
     */
    public LLMProvider getEmbeddingProvider() {
        if ("hybrid".equalsIgnoreCase(providerName)) {
            return ollamaProvider; // Use local Ollama for embeddings
        }
        return getProvider(); // Otherwise use main provider
    }

    /**
     * Get provider for tool selection (fast iterations).
     * In hybrid mode, use Ollama for fast tool selection.
     */
    public LLMProvider getToolSelectionProvider() {
        if ("hybrid".equalsIgnoreCase(providerName)) {
            return ollamaProvider; // Use local Ollama for tool selection
        }
        return getProvider(); // Otherwise use main provider
    }

    /**
     * Get provider for final response generation (quality matters).
     * In hybrid mode, use Gemini for final high-quality response.
     */
    public LLMProvider getFinalResponseProvider() {
        if ("hybrid".equalsIgnoreCase(providerName)) {
            return geminiProvider; // Use Gemini for final response
        }
        return getProvider(); // Otherwise use main provider
    }

    /**
     * Get a specific provider by name (for explicit routing).
     */
    public LLMProvider getProvider(String name) {
        return switch (name.toLowerCase()) {
            case "ollama" -> ollamaProvider;
            case "gemini" -> geminiProvider;
            default -> throw new IllegalArgumentException("Unknown LLM provider: " + name);
        };
    }
}
