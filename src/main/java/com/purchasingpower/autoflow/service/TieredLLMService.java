package com.purchasingpower.autoflow.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Tiered LLM Service - Routes requests to appropriate model based on task complexity.
 *
 * DECISION TREE:
 * - Simple/Routine → Ollama (local, free, fast)
 * - Complex/User-facing → Gemini (cloud, paid, high-quality)
 *
 * EXAMPLE ROUTING:
 * - "Which tool should I use?" → Ollama
 * - "Parse this JSON response" → Ollama
 * - "Explain how this code works to the user" → Gemini
 *
 * @since 2.0.0
 */
@Slf4j
@Service
public class TieredLLMService {

    private final ChatLanguageModel toolSelectionModel;

    @Autowired
    public TieredLLMService(
            @Qualifier("toolSelectionModel") ChatLanguageModel toolSelectionModel) {
        this.toolSelectionModel = toolSelectionModel;
        log.info("✅ TieredLLMService initialized with local tool selection model");
    }

    /**
     * Use the fast local model for tool selection.
     *
     * This saves Gemini API calls for routine decision-making.
     *
     * @param prompt The tool selection prompt
     * @return The model's tool choice
     */
    public String selectTool(String prompt) {
        log.debug("🔧 [TOOL SELECTION] Using local model (Ollama)");
        long start = System.currentTimeMillis();

        String response = toolSelectionModel.generate(prompt);

        long duration = System.currentTimeMillis() - start;
        log.debug("✅ [TOOL SELECTION] Completed in {}ms (LOCAL - no API cost)", duration);

        return response;
    }

    /**
     * Use the fast local model for parsing structured data.
     *
     * Example: Extract JSON from tool response, parse error messages, etc.
     *
     * @param prompt The parsing prompt
     * @return The parsed result
     */
    public String parseResponse(String prompt) {
        log.debug("🔧 [PARSING] Using local model (Ollama)");
        long start = System.currentTimeMillis();

        String response = toolSelectionModel.generate(prompt);

        long duration = System.currentTimeMillis() - start;
        log.debug("✅ [PARSING] Completed in {}ms (LOCAL - no API cost)", duration);

        return response;
    }

    /**
     * Use the fast local model to determine if more information is needed.
     *
     * Example: "Given these search results, do we have enough info to answer the question?"
     *
     * @param prompt The decision prompt
     * @return The model's decision
     */
    public String makeRoutineDecision(String prompt) {
        log.debug("🔧 [DECISION] Using local model (Ollama)");
        long start = System.currentTimeMillis();

        String response = toolSelectionModel.generate(prompt);

        long duration = System.currentTimeMillis() - start;
        log.debug("✅ [DECISION] Completed in {}ms (LOCAL - no API cost)", duration);

        return response;
    }

    /**
     * Check if the local Ollama model is available.
     *
     * @return true if Ollama is running and responding
     */
    public boolean isLocalModelAvailable() {
        try {
            toolSelectionModel.generate("ping");
            return true;
        } catch (Exception e) {
            log.warn("⚠️  Local model (Ollama) is not available: {}", e.getMessage());
            return false;
        }
    }
}
