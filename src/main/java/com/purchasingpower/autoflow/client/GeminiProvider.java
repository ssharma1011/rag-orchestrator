package com.purchasingpower.autoflow.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Gemini LLM provider implementation (wrapper for GeminiClient).
 *
 * Uses Google's Gemini API for chat and embeddings.
 * Suitable for cloud-based workloads with API quota.
 *
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiProvider implements LLMProvider {

    private final GeminiClient geminiClient;

    @Override
    public String chat(String prompt, String agentName, String conversationId) {
        return geminiClient.callChatApi(prompt, agentName, conversationId);
    }

    @Override
    public List<Double> embed(String text) {
        return geminiClient.createEmbedding(text);
    }

    @Override
    public List<List<Double>> embedBatch(List<String> texts) {
        return geminiClient.batchCreateEmbeddings(texts);
    }

    @Override
    public String getProviderName() {
        return "Gemini";
    }
}
