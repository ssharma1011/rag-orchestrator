package com.purchasingpower.autoflow.configuration;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Tiered LLM Configuration (Gemini's recommendation).
 *
 * STRATEGY:
 * - Use LOCAL Ollama for cheap, fast operations (tool selection, parsing)
 * - Reserve CLOUD Gemini for expensive reasoning (final explanations, code generation)
 *
 * BENEFITS:
 * - Reduces API calls to Gemini by ~70%
 * - Eliminates 429 rate limit errors on routine operations
 * - Maintains high quality for user-facing responses
 *
 * USAGE:
 * <pre>
 * {@code
 * @Autowired
 * @Qualifier("toolSelectionModel")
 * private ChatLanguageModel toolModel;  // Fast local model
 *
 * @Autowired
 * @Qualifier("reasoningModel")
 * private ChatLanguageModel reasoningModel;  // High-quality cloud model
 * }
 * </pre>
 *
 * @since 2.0.0
 */
@Slf4j
@Configuration
public class TieredLLMConfiguration {

    @Value("${app.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${app.ollama.chat-model:qwen2.5-coder:1.5b}")
    private String ollamaChatModel;

    @Value("${app.ollama.timeout-seconds:120}")
    private int ollamaTimeoutSeconds;

    @Value("${app.ollama.max-retries:3}")
    private int ollamaMaxRetries;

    /**
     * TIER 1: Fast local model for tool selection, code parsing, and routing.
     *
     * USE FOR:
     * - Deciding which tool to call next
     * - Parsing tool responses into structured data
     * - Determining if more information is needed
     * - Analyzing code structure (AST traversal decisions)
     *
     * DO NOT USE FOR:
     * - Final code explanations to users
     * - Code generation (unless simple)
     * - Complex reasoning tasks
     *
     * MODEL: qwen2.5-coder:1.5b (fast, good at code understanding)
     * COST: FREE (runs locally)
     * SPEED: ~50-100 tokens/sec
     */
    @Bean("toolSelectionModel")
    public ChatLanguageModel toolSelectionModel() {
        log.info("🔧 Initializing Tool Selection Model (Ollama - Local)");
        log.info("   - URL: {}", ollamaBaseUrl);
        log.info("   - Model: {}", ollamaChatModel);
        log.info("   - Use case: Tool selection, parsing, routing");

        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaChatModel)
                .timeout(Duration.ofSeconds(ollamaTimeoutSeconds))
                .temperature(0.0)  // Deterministic for tool selection
                .maxRetries(ollamaMaxRetries)
                .logRequests(false)
                .logResponses(false)
                .build();

        log.info("✅ Tool Selection Model initialized");
        return model;
    }

    /**
     * TIER 2: High-quality cloud model for final reasoning and user-facing responses.
     *
     * NOTE: This bean is intentionally commented out because your existing
     * GeminiClient is already handling this role. To enable LangChain4j for Gemini,
     * you would need to:
     *
     * 1. Add dependency: langchain4j-vertex-ai-gemini
     * 2. Uncomment this bean
     * 3. Update AutoFlowAgent to use this instead of GeminiClient
     *
     * For now, keeping your existing GeminiClient to minimize breaking changes.
     */
    /*
    @Bean("reasoningModel")
    public ChatLanguageModel reasoningModel(
            @Value("${app.gemini.project-id}") String projectId,
            @Value("${app.gemini.location:us-central1}") String location,
            @Value("${app.gemini.chat-model:gemini-1.5-pro}") String modelName) {

        log.info("🧠 Initializing Reasoning Model (Gemini - Cloud)");
        log.info("   - Project: {}", projectId);
        log.info("   - Model: {}", modelName);
        log.info("   - Use case: Final reasoning, code generation");

        ChatLanguageModel model = VertexAiGeminiChatModel.builder()
                .project(projectId)
                .location(location)
                .modelName(modelName)
                .temperature(0.7)
                .maxRetries(3)
                .build();

        log.info("✅ Reasoning Model initialized");
        return model;
    }
    */

    /**
     * Example usage pattern for tiered models:
     *
     * <pre>
     * public class AutoFlowAgent {
     *
     *     @Autowired
     *     @Qualifier("toolSelectionModel")
     *     private ChatLanguageModel toolModel;
     *
     *     @Autowired
     *     private GeminiClient geminiClient;  // Keep existing for now
     *
     *     public String processQuery(String userQuery) {
     *         // STEP 1: Use local model to decide which tool to call (FREE)
     *         String toolDecision = toolModel.generate(
     *             "Given query: " + userQuery + ", which tool should we call? " +
     *             "Options: search_code, semantic_search, discover_project"
     *         );
     *
     *         // STEP 2: Execute the tool
     *         String toolResult = executeTool(toolDecision);
     *
     *         // STEP 3: Use Gemini for final high-quality explanation (PAID)
     *         return geminiClient.generateExplanation(userQuery, toolResult);
     *     }
     * }
     * </pre>
     */
}
