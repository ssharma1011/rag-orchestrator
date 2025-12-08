package com.purchasingpower.autoflow.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.purchasingpower.autoflow.configuration.AppProperties;
import com.purchasingpower.autoflow.model.llm.CodeGenerationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final AppProperties props;
    private final ObjectMapper objectMapper;
    // Using Global Endpoint (Compatible with AI Studio API Keys)
    private final WebClient geminiWebClient = WebClient.create("https://generativelanguage.googleapis.com");

    // -----------------------------------------------------------------------
    // 1. EMBEDDING (Vector Logic)
    // -----------------------------------------------------------------------
    public List<Double> createEmbedding(String text) {
        String model = props.getGemini().getEmbeddingModel();
        String url = "/v1beta/models/" + model + ":embedContent?key=" + props.getGemini().getApiKey();

        Map<String, Object> body = Map.of(
                "model", "models/" + model,
                "content", Map.of("parts", List.of(Map.of("text", text)))
        );

        try {
            JsonNode response = geminiWebClient.post().uri(url).bodyValue(body)
                    .retrieve().bodyToMono(JsonNode.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)).filter(this::isRetryable))
                    .block();

            return objectMapper.convertValue(
                    response.path("embedding").path("values"),
                    List.class
            );
        } catch (Exception e) {
            log.error("Embedding failed", e);
            throw new RuntimeException("Failed to embed requirements");
        }
    }

    // -----------------------------------------------------------------------
    // 2. SCAFFOLDING (New Projects) - The "Architect" Mode
    // -----------------------------------------------------------------------
    public CodeGenerationResponse generateScaffold(String requirements, String repoName) {
        String prompt = buildArchitectPrompt(requirements, repoName);
        return callChatApi(prompt);
    }

    private String buildArchitectPrompt(String requirements, String repoName) {
        return """
            SYSTEM: You are a Lead Software Architect.
            TASK: Initialize a NEW Spring Boot 3 Microservice from scratch based on the requirements.
            
            REPO NAME: %s
            INPUT REQUIREMENTS (Jira): %s
            
            --- CRITICAL INSTRUCTIONS (DEPENDENCY CHAIN) ---
            You must generate a complete, compiling "Vertical Slice" for every feature requested.
            
            1. **Analyze Nouns**: Identify the core domain entities from the requirements (e.g., if it says "Manage Inventory", the entity is 'Inventory').
            2. **Enforce Layers**: For every Entity identified, you MUST generate:
               - The **Entity** Class (JPA/Hibernate)
               - The **Repository** Interface (extending JpaRepository)
               - The **Service** Class (Business Logic)
               - The **Controller** Class (REST Endpoints)
            3. **Completeness Protocol**: 
               - Do NOT leave files as comments.
               - If Class A imports Class B, **you MUST generate Class B** in the 'edits' list.
               - Ensure the package structure is consistent (e.g., com.product.management...).
            
            OUTPUT SCHEMA (Strict JSON):
            {
              "branch_name": "feat/init-project",
              "edits": [
                 { "path": "pom.xml", "op": "create", "content": "<FULL_XML>" },
                 { "path": "src/main/java/<PACKAGE_PATH>/Application.java", "op": "create", "content": "<FULL_JAVA>" },
                 // EXAMPLE: GENERATE ALL LAYERS FOR DETECTED ENTITIES
                 { "path": "src/main/java/<PACKAGE_PATH>/model/<EntityName>.java", "op": "create", "content": "<FULL_JAVA>" },
                 { "path": "src/main/java/<PACKAGE_PATH>/repository/<EntityName>Repository.java", "op": "create", "content": "<FULL_JAVA>" },
                 { "path": "src/main/java/<PACKAGE_PATH>/service/<EntityName>Service.java", "op": "create", "content": "<FULL_JAVA>" },
                 { "path": "src/main/java/<PACKAGE_PATH>/controller/<EntityName>Controller.java", "op": "create", "content": "<FULL_JAVA>" }
              ],
              "tests_added": [
                 { "path": "src/test/java/<PACKAGE_PATH>/<EntityName>ControllerTest.java", "content": "<FULL_JAVA>" }
              ],
              "explanation": "I created the full stack for [Entity] based on requirements."
            }
            """.formatted(repoName, requirements);
    }

    // -----------------------------------------------------------------------
    // 3. MAINTENANCE (Existing Projects) - The "Maintainer" Mode
    // -----------------------------------------------------------------------
    public CodeGenerationResponse generateCodePlan(String requirements, String context) {
        String prompt = buildMaintainerPrompt(requirements, context);
        return callChatApi(prompt);
    }

    private String buildMaintainerPrompt(String req, String ctx) {
        return """
            SYSTEM: You are a Senior Java Engineer working on an existing Spring Boot codebase.
            
            GOAL: Implement the features described in REQUIREMENTS.
            
            CONTEXT (Existing Code Snippets retrieved via RAG):
            %s
            
            REQUIREMENTS (Jira):
            %s
            
            INSTRUCTIONS:
            1. **Analyze Context**: Use the provided files to understand the ACTUAL project package structure and libraries.
            2. **Modifications**: If an existing class needs changes, output an edit with "op": "modify".
            3. **New Files (CRITICAL)**: 
               - If the requirement implies a new DTO, Entity, or Service that does NOT exist in the context, **YOU MUST CREATE IT**.
               - Use "op": "create".
               - **IMPORTANT**: Use the SAME package structure found in the Context.
            
            OUTPUT SCHEMA (Strict JSON):
            {
              "branch_name": "feat/<jira-key>-implementation",
              "edits": [ 
                // PATTERN 1: Modify existing file (Use actual paths from Context)
                { "path": "<EXISTING_FILE_PATH>", "op": "modify", "content": "<FULL_UPDATED_CONTENT>" },
                
                // PATTERN 2: Create NEW file (Derive path from Context structure)
                { "path": "<NEW_FILE_PATH>", "op": "create", "content": "<FULL_NEW_CONTENT>" }
              ],
              "tests_added": [
                 { "path": "<TEST_PATH>", "content": "<FULL_TEST_CONTENT>" }
              ],
              "explanation": "Brief summary of changes."
            }
            """.formatted(ctx, req);
    }

    // -----------------------------------------------------------------------
    // SHARED API CALL LOGIC
    // -----------------------------------------------------------------------
    private CodeGenerationResponse callChatApi(String prompt) {
        String model = props.getGemini().getChatModel();
        String url = "/v1beta/models/" + model + ":generateContent?key=" + props.getGemini().getApiKey();

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.2,
                        // ✅ CRITICAL: High token limit (8192) to prevent JSON truncation
                        "maxOutputTokens", 8192
                )
        );

        try {
            String json = geminiWebClient.post().uri(url).bodyValue(body)
                    .retrieve().bodyToMono(String.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(10)).filter(this::isRetryable))
                    .block();

            return parseResponse(json);
        } catch (Exception e) {
            log.error("Gemini Chat failed", e);
            throw new RuntimeException("Failed to generate code plan");
        }
    }

    private boolean isRetryable(Throwable ex) {
        return ex instanceof WebClientResponseException.TooManyRequests ||
                ex instanceof WebClientResponseException.InternalServerError ||
                ex instanceof WebClientResponseException.BadGateway ||
                ex instanceof WebClientResponseException.ServiceUnavailable;
    }

    private CodeGenerationResponse parseResponse(String rawJson) throws Exception {
        JsonNode root = objectMapper.readTree(rawJson);

        if (!root.path("promptFeedback").path("blockReason").isMissingNode()) {
            throw new RuntimeException("Gemini Blocked content: " + root.path("promptFeedback").toString());
        }

        JsonNode candidates = root.path("candidates");
        if (candidates.isEmpty()) {
            throw new RuntimeException("Gemini returned no candidates. The model may have refused the prompt.");
        }

        String content = candidates.get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();

        return objectMapper.readValue(content, CodeGenerationResponse.class);
    }


    public CodeGenerationResponse generateFix(String buildLogs, String originalRequirements) {
        String prompt = """
            SYSTEM: You are a Senior Java Engineer.
            TASK: Fix the compilation errors in the code you generated.
            
            --- ORIGINAL REQUIREMENTS ---
            %s
            
            --- COMPILER ERROR LOGS ---
            %s
            
            INSTRUCTIONS:
            1. Analyze the error logs (look for 'cannot find symbol', 'syntax error', etc).
            2. Generate JSON patches to FIX these specific errors.
            3. **Do NOT regenerate the whole project.** Only fix the broken files or create missing ones.
            4. If the log says "package com.example.model does not exist", create that package and class.
            
            OUTPUT SCHEMA (Strict JSON):
            {
              "branch_name": "feat/fix-compile-errors",
              "edits": [
                 { "path": "src/main/java/.../BrokenClass.java", "op": "modify", "content": "<FIXED_CONTENT>" }
              ],
              "tests_added": [],
              "explanation": "Fixed missing import / syntax error."
            }
            """.formatted(originalRequirements, buildLogs);

        return callChatApi(prompt);
    }
}