package com.purchasingpower.autoflow.service.impl;

import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.model.conversation.ConversationContext;
import com.purchasingpower.autoflow.model.conversation.ConversationMessage;
import com.purchasingpower.autoflow.model.conversation.ConversationState;
import com.purchasingpower.autoflow.model.jira.JiraIssueDetails;
import com.purchasingpower.autoflow.repository.ConversationContextRepository;
import com.purchasingpower.autoflow.service.ConversationService;
import com.purchasingpower.autoflow.service.JiraClientService;
import com.purchasingpower.autoflow.service.graph.GraphTraversalService;
import com.purchasingpower.autoflow.workflow.pipeline.PipelineEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of ConversationService.
 * Manages interactive conversations between developers and LLM via JIRA comments.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final JiraClientService jiraService;
    private final GeminiClient geminiClient;
    private final GraphTraversalService graphTraversal;
    private final PipelineEngine pipelineEngine;
    private final ConversationContextRepository conversationRepo;

    @Override
    @Transactional
    public void handleMessage(String issueKey, String message, String author) {
        log.info("Processing message from {} on {}: '{}'",
                author, issueKey, truncate(message, 50));

        try {
            // Load or create conversation
            ConversationContext context = conversationRepo.findById(issueKey)
                    .orElseGet(() -> startNewConversation(issueKey));

            // Add user message to history
            context.addMessage(new ConversationMessage("user", message));

            // Check if it's an info query (question) or action request
            if (isInfoQuery(message)) {
                handleInfoQuery(context, message);
            } else if (isCodeGenerationRequest(message)) {
                handleCodeGenerationRequest(context, message);
            } else if (isAnalysisRequest(message)) {
                handleAnalysisRequest(context, message);
            } else {
                // Generic LLM response
                handleGenericQuery(context, message);
            }

            // Save conversation
            conversationRepo.save(context);

        } catch (Exception e) {
            log.error("Failed to process message for {}", issueKey, e);
            jiraService.addComment(issueKey,
                    "❌ Error processing your request: " + e.getMessage()).subscribe();
        }
    }

    @Override
    public ConversationContext getConversation(String issueKey) {
        return conversationRepo.findById(issueKey).orElse(null);
    }

    @Override
    public boolean conversationExists(String issueKey) {
        return conversationRepo.existsById(issueKey);
    }

    @Override
    @Transactional
    public void deleteConversation(String issueKey) {
        conversationRepo.deleteById(issueKey);
        log.info("Deleted conversation for {}", issueKey);
    }


    private ConversationContext startNewConversation(String issueKey) {
        log.info("Starting new conversation for {}", issueKey);

        // Fetch JIRA issue
        JiraIssueDetails issue = jiraService.getIssue(issueKey).block();
        if (issue == null) {
            throw new IllegalStateException("Could not fetch JIRA issue: " + issueKey);
        }

        String requirements = issue.getFields().getDescriptionText();
        String repoName = extractRepoName(issue);

        ConversationContext context = new ConversationContext();
        context.setIssueKey(issueKey);
        context.setState(ConversationState.INITIAL_ANALYSIS);
        context.setRequirements(requirements);
        context.setRepoName(repoName);
        context.setMessages(new ArrayList<>());
        context.setLastUpdated(LocalDateTime.now());

        return context;
    }

    private void handleInfoQuery(ConversationContext context, String query) {
        log.info("Handling info query: {}", truncate(query, 50));

        // Build conversation history for LLM
        String conversationHistory = buildConversationHistory(context);

        // Ask LLM to answer based on previous context
        String prompt = String.format("""
                You are AutoFlow AI assistant helping a developer.
                
                CONVERSATION HISTORY:
                %s
                
                DEVELOPER QUESTION:
                %s
                
                Answer the question based on the codebase analysis and conversation so far.
                Be specific and technical. Include class names and file paths if relevant.
                Keep response concise (under 500 words).
                """, conversationHistory, query);

        String response = geminiClient.generateText(prompt);

        // Post response to JIRA
        jiraService.addComment(context.getIssueKey(),
                formatAsJiraComment(response, "💬 Response")).subscribe();

        // Add to conversation
        context.addMessage(new ConversationMessage("assistant", response));
    }

    private void handleCodeGenerationRequest(ConversationContext context, String message) {
        log.info("Handling code generation request for {}", context.getIssueKey());

        // Update state
        context.setState(ConversationState.GENERATING_CODE);
        conversationRepo.save(context);

        // Notify JIRA
        jiraService.addComment(context.getIssueKey(),
                "✅ Starting code generation...").subscribe();

        // Trigger pipeline
        pipelineEngine.run(context.getIssueKey());

        // Update state
        context.setState(ConversationState.COMPLETED);
    }

    private void handleAnalysisRequest(ConversationContext context, String message) {
        log.info("Handling analysis request for {}", context.getIssueKey());

        String repoName = context.getRepoName();

        // Analyze codebase using graph
        String analysis = analyzeCodebase(repoName, context.getRequirements());

        // Generate clarifying questions
        String questions = generateClarifyingQuestions(
                context.getRequirements(),
                analysis
        );

        // Post to JIRA
        String response = String.format("""
                🤖 **Codebase Analysis Complete**
                
                %s
                
                **Clarifying Questions:**
                %s
                
                Reply with answers or ask follow-up questions.
                When ready, say "@autoflow generate code" to proceed.
                """, analysis, questions);

        jiraService.addComment(context.getIssueKey(), response).subscribe();

        // Save analysis
        context.setCodebaseAnalysis(analysis);
        context.setState(ConversationState.AWAITING_CLARIFICATION);
        context.addMessage(new ConversationMessage("assistant", response));
    }

    private void handleGenericQuery(ConversationContext context, String message) {
        log.info("Handling generic query for {}", context.getIssueKey());

        // Build full conversation
        String conversationHistory = buildConversationHistory(context);

        String prompt = String.format("""
                You are AutoFlow AI assistant.
                
                CONVERSATION HISTORY:
                %s
                
                LATEST MESSAGE:
                %s
                
                Respond helpfully. If unclear, ask clarifying questions.
                """, conversationHistory, message);

        String response = geminiClient.generateText(prompt);

        jiraService.addComment(context.getIssueKey(),
                formatAsJiraComment(response, "🤖 AutoFlow")).subscribe();

        context.addMessage(new ConversationMessage("assistant", response));
    }

    // ================================================================
    // HELPER METHODS
    // ================================================================

    private boolean isInfoQuery(String message) {
        String lower = message.toLowerCase();
        return lower.contains("what") ||
                lower.contains("which") ||
                lower.contains("how many") ||
                lower.contains("show me") ||
                lower.contains("list") ||
                lower.contains("?");
    }

    private boolean isCodeGenerationRequest(String message) {
        String lower = message.toLowerCase();
        return lower.contains("generate code") ||
                lower.contains("create pr") ||
                lower.contains("implement") ||
                lower.contains("write code") ||
                lower.contains("approved");
    }

    private boolean isAnalysisRequest(String message) {
        String lower = message.toLowerCase();
        return lower.contains("analyze") ||
                lower.contains("analysis") ||
                lower.contains("scan") ||
                lower.contains("review codebase");
    }

    private String analyzeCodebase(String repoName, String requirements) {
        StringBuilder analysis = new StringBuilder();
        analysis.append("**Codebase Analysis:**\n\n");

        try {
            // Find relevant classes by role
            if (requirements.toLowerCase().contains("kafka")) {
                List<String> kafkaClasses = new ArrayList<>();
                kafkaClasses.addAll(graphTraversal.findByRole("spring-kafka:consumer", repoName));
                kafkaClasses.addAll(graphTraversal.findByRole("spring-kafka:producer", repoName));

                if (!kafkaClasses.isEmpty()) {
                    analysis.append("Kafka Components: ").append(kafkaClasses.size()).append("\n");
                    kafkaClasses.stream().limit(5).forEach(c ->
                            analysis.append("  - ").append(c).append("\n"));
                }
            }

            if (requirements.toLowerCase().contains("rest") ||
                    requirements.toLowerCase().contains("controller")) {
                List<String> controllers = graphTraversal.findByRole("spring:rest-controller", repoName);
                if (!controllers.isEmpty()) {
                    analysis.append("REST Controllers: ").append(controllers.size()).append("\n");
                    controllers.stream().limit(5).forEach(c ->
                            analysis.append("  - ").append(c).append("\n"));
                }
            }

        } catch (Exception e) {
            log.warn("Could not analyze codebase (graph not indexed?): {}", e.getMessage());
            analysis.append("Note: Codebase not yet indexed. Run indexing first.\n");
        }

        return analysis.toString();
    }

    private String generateClarifyingQuestions(String requirements, String analysis) {
        String prompt = String.format("""
                You are a senior software architect.
                
                TASK: %s
                
                CODEBASE ANALYSIS: %s
                
                Generate 3-5 clarifying questions to better understand requirements.
                Focus on technical decisions, scope, and integration points.
                Format as numbered list.
                """, requirements, analysis);

        return geminiClient.generateText(prompt);
    }

    private String buildConversationHistory(ConversationContext context) {
        return context.getMessages().stream()
                .map(m -> m.getRole().toUpperCase() + ": " + m.getContent())
                .collect(Collectors.joining("\n\n"));
    }

    private String extractRepoName(JiraIssueDetails issue) {
        Object repoName = issue.getFields().getCustomFields().get("customfield_10141");
        return repoName != null ? repoName.toString() : "unknown";
    }

    private String formatAsJiraComment(String content, String title) {
        return String.format("""
                h3. %s
                
                %s
                
                ---
                _Generated by AutoFlow AI_
                """, title, content);
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}