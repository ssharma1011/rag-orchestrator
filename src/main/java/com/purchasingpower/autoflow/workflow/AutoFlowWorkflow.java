package com.purchasingpower.autoflow.workflow;

import com.purchasingpower.autoflow.model.dto.WorkflowEvent;
import com.purchasingpower.autoflow.service.WorkflowStreamService;
import com.purchasingpower.autoflow.workflow.agents.*;
import com.purchasingpower.autoflow.workflow.state.AgentDecision;
import com.purchasingpower.autoflow.workflow.state.RequirementAnalysis;
import com.purchasingpower.autoflow.workflow.state.WorkflowState;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Slf4j
@Component
public class AutoFlowWorkflow {

    private final RequirementAnalyzerAgent requirementAnalyzer;
    private final LogAnalyzerAgent logAnalyzer;
    private final CodeIndexerAgent codeIndexer;
    private final ScopeDiscoveryAgent scopeDiscovery;
    private final ScopeApprovalAgent scopeApproval;
    private final ContextBuilderAgent contextBuilder;
    private final CodeGeneratorAgent codeGenerator;
    private final BuildValidatorAgent buildValidator;
    private final TestRunnerAgent testRunner;
    private final PRReviewerAgent prReviewer;
    private final ReadmeGeneratorAgent readmeGenerator;
    private final PRCreatorAgent prCreator;
    private final DocumentationAgent documentationAgent;

    /**
     * SSE streaming service (optional - may be null if SSE not enabled).
     */
    @Autowired(required = false)
    private WorkflowStreamService streamService;

    private CompiledGraph<WorkflowState> compiledGraph;

    public AutoFlowWorkflow(
            RequirementAnalyzerAgent requirementAnalyzer,
            LogAnalyzerAgent logAnalyzer,
            CodeIndexerAgent codeIndexer,
            ScopeDiscoveryAgent scopeDiscovery,
            ScopeApprovalAgent scopeApproval,
            ContextBuilderAgent contextBuilder,
            CodeGeneratorAgent codeGenerator,
            BuildValidatorAgent buildValidator,
            TestRunnerAgent testRunner,
            PRReviewerAgent prReviewer,
            ReadmeGeneratorAgent readmeGenerator,
            PRCreatorAgent prCreator,
            DocumentationAgent documentationAgent
    ) {
        this.requirementAnalyzer = requirementAnalyzer;
        this.logAnalyzer = logAnalyzer;
        this.codeIndexer = codeIndexer;
        this.scopeDiscovery = scopeDiscovery;
        this.scopeApproval = scopeApproval;
        this.contextBuilder = contextBuilder;
        this.codeGenerator = codeGenerator;
        this.buildValidator = buildValidator;
        this.testRunner = testRunner;
        this.prReviewer = prReviewer;
        this.readmeGenerator = readmeGenerator;
        this.prCreator = prCreator;
        this.documentationAgent = documentationAgent;
    }

    @PostConstruct
    public void initialize() throws GraphStateException {
        log.info("🚀 Initializing AutoFlow workflow graph...");
        StateGraph<WorkflowState> graph = new StateGraph<>(WorkflowState::new);

        graph.addNode("requirement_analyzer", node_async(requirementAnalyzer::execute));
        graph.addNode("log_analyzer", node_async(logAnalyzer::execute));
        graph.addNode("code_indexer", node_async(codeIndexer::execute));
        graph.addNode("scope_discovery", node_async(scopeDiscovery::execute));
        graph.addNode("scope_approval", node_async(scopeApproval::execute));
        graph.addNode("context_builder", node_async(contextBuilder::execute));
        graph.addNode("code_generator", node_async(codeGenerator::execute));
        graph.addNode("build_validator", node_async(buildValidator::execute));
        graph.addNode("test_runner", node_async(testRunner::execute));
        graph.addNode("pr_reviewer", node_async(prReviewer::execute));
        graph.addNode("readme_generator", node_async(readmeGenerator::execute));
        graph.addNode("pr_creator", node_async(prCreator::execute));
        graph.addNode("documentation_agent", node_async(documentationAgent::execute));


        graph.addNode("ask_developer", node_async(s -> {
            Map<String, Object> updates = new java.util.HashMap<>(s.toMap());
            updates.put("workflowStatus", "PAUSED");
            return updates;
        }));

        graph.addNode("chat_responder", node_async(s -> {
            log.info("💬 Responding to casual chat message");
            Map<String, Object> updates = new java.util.HashMap<>(s.toMap());
            updates.put("lastAgentDecision", AgentDecision.endSuccess("👋 Hello! I'm ready to help with your codebase. What would you like to work on?"));
            updates.put("workflowStatus", "COMPLETED");
            return updates;
        }));

        // Define Edges
        graph.addEdge(START, "requirement_analyzer");

        graph.addConditionalEdges("requirement_analyzer",
                edge_async(s -> {
                    RequirementAnalysis analysis = s.getRequirementAnalysis();

                    log.info("═══════════════════════════════════════════════════════");
                    log.info("🔀 CAPABILITY-BASED ROUTING:");
                    if (analysis != null) {
                        log.info("   Task Type: {}", analysis.getTaskType());
                        log.info("   Data Sources: {}", analysis.getDataSources());
                        log.info("   Modifies Code: {}", analysis.isModifiesCode());
                        log.info("   Needs Approval: {}", analysis.isNeedsApproval());
                    }
                    log.info("═══════════════════════════════════════════════════════");

                    // CAPABILITY-BASED ROUTING (not task type strings)
                    if (analysis != null) {
                        // Casual chat - no data sources needed
                        if (analysis.isCasualChat()) {
                            log.info("💬 Casual chat → chat_responder");
                            return "chat_responder";
                        }

                        // Read-only query needing code context
                        if (analysis.isReadOnly() && analysis.needsCodeContext()) {
                            log.info("📚 Read-only code query → code_indexer");
                            return "code_indexer";
                        }

                        // Code modification - full workflow
                        if (analysis.isModifiesCode() && analysis.needsCodeContext()) {
                            log.info("🔧 Code modification → {}", s.hasLogs() ? "log_analyzer" : "code_indexer");
                            return s.hasLogs() ? "log_analyzer" : "code_indexer";
                        }

                        // Future: Confluence-only queries
                        if (analysis.needsConfluenceContext() && !analysis.needsCodeContext()) {
                            log.info("📋 Confluence query → confluence_handler (TODO)");
                            return "chat_responder"; // For now
                        }
                    }

                    if (shouldPause(s)) {
                        return "ask_developer";
                    }
                    return s.hasLogs() ? "log_analyzer" : "code_indexer";
                }),
                Map.of(
                        "chat_responder", "chat_responder",
                        "ask_developer", "ask_developer",
                        "log_analyzer", "log_analyzer",
                        "code_indexer", "code_indexer"
                )
        );
        graph.addEdge("log_analyzer", "code_indexer");

        graph.addConditionalEdges("code_indexer",
                edge_async(s -> {
                    RequirementAnalysis analysis = s.getRequirementAnalysis();
                    log.info("═══════════════════════════════════════════════════════");
                    log.info("🔀 ROUTING FROM CODE_INDEXER:");
                    if (analysis != null) {
                        log.info("   Read-only: {}", analysis.isReadOnly());
                        log.info("   Modifies code: {}", analysis.isModifiesCode());
                    }
                    log.info("═══════════════════════════════════════════════════════");

                    // If read-only query, route to documentation agent
                    if (analysis != null && analysis.isReadOnly()) {
                        log.info("📚 Read-only query → documentation_agent");
                        return "documentation_agent";
                    }

                    if (shouldPause(s)) {
                        return "ask_developer";
                    }

                    // CRITICAL FIX: If scope proposal exists and user just responded, validate approval
                    if (s.getScopeProposal() != null && userJustResponded(s)) {
                        log.info("✅ Scope proposal exists + user responded → Validating approval");
                        return "scope_approval";
                    }

                    // Otherwise, run scope discovery
                    return "scope_discovery";
                }),
                Map.of(
                        "scope_discovery", "scope_discovery",
                        "scope_approval", "scope_approval",
                        "documentation_agent", "documentation_agent",
                        "ask_developer", "ask_developer"
                )
        );

        graph.addEdge("documentation_agent", END);

        graph.addConditionalEdges("scope_discovery",
                edge_async(s -> shouldPause(s) ? "ask_developer" : "context_builder"),
                Map.of("context_builder", "context_builder", "ask_developer", "ask_developer"));

        graph.addConditionalEdges("scope_approval",
                edge_async(s -> shouldPause(s) ? "ask_developer" : "context_builder"),
                Map.of("context_builder", "context_builder", "ask_developer", "ask_developer"));

        graph.addConditionalEdges("context_builder",
                edge_async(s -> shouldPause(s) ? "ask_developer" : "code_generator"),
                Map.of("code_generator", "code_generator", "ask_developer", "ask_developer"));

        graph.addEdge("code_generator", "build_validator");

        graph.addConditionalEdges("build_validator",
                edge_async(this::routeFromBuildValidator),
                Map.of("test_runner", "test_runner", "code_generator", "code_generator", "ask_developer", "ask_developer"));

        graph.addConditionalEdges("test_runner",
                edge_async(s -> shouldPause(s) ? "ask_developer" : "pr_reviewer"),
                Map.of("pr_reviewer", "pr_reviewer", "ask_developer", "ask_developer"));

        graph.addConditionalEdges("pr_reviewer",
                edge_async(this::routeFromPRReviewer),
                Map.of("readme_generator", "readme_generator", "code_generator", "code_generator", "ask_developer", "ask_developer"));

        graph.addEdge("readme_generator", "pr_creator");
        graph.addEdge("pr_creator", END);

        graph.addEdge("ask_developer", END);
        graph.addEdge("chat_responder", END);

        this.compiledGraph = graph.compile();
    }

    public WorkflowState execute(WorkflowState initialState) {
        String conversationId = initialState.getConversationId();
        log.info("🚀 Starting workflow: {}", conversationId);

        // Send SSE: Workflow started
        sendSSE(conversationId, WorkflowEvent.running(
                conversationId,
                "workflow",
                "🚀 Workflow started",
                0.0
        ));

        Map<String, Object> initialData = new java.util.HashMap<>(initialState.toMap());
        initialData.put("workflowStatus", "RUNNING");
        initialData.put("buildAttempt", 0);
        initialData.put("reviewAttempt", 0);

        try {
            Optional<WorkflowState> result = compiledGraph.invoke(initialData);
            WorkflowState finalState = result.orElse(initialState);

            // Send SSE: Workflow completed
            String completionMessage = finalState.getLastAgentDecision() != null ?
                    finalState.getLastAgentDecision().getExplanation() :
                    "✅ Workflow completed successfully";

            if (streamService != null) {
                streamService.complete(conversationId, completionMessage);
            }

            return finalState;

        } catch (Exception e) {
            log.error("Workflow execution failed", e);

            // Send SSE: Workflow failed
            if (streamService != null) {
                streamService.fail(conversationId, e.getMessage());
            }

            Map<String, Object> errorData = new java.util.HashMap<>(initialData);
            errorData.put("workflowStatus", "FAILED");
            errorData.put("lastAgentDecision", AgentDecision.error(e.getMessage()));
            return WorkflowState.fromMap(errorData);
        }
    }

    /**
     * Send SSE update (if streaming is enabled).
     */
    private void sendSSE(String conversationId, WorkflowEvent event) {
        if (streamService != null) {
            streamService.sendUpdate(conversationId, event);
        }
    }

    private boolean shouldPause(WorkflowState state) {
        return state.getLastAgentDecision() != null &&
                state.getLastAgentDecision().getNextStep() == AgentDecision.NextStep.ASK_DEV;
    }

    /**
     * Check if user just responded to a question.
     * Used to detect when we need to validate approval instead of re-running agents.
     */
    private boolean userJustResponded(WorkflowState state) {
        var history = state.getConversationHistory();
        if (history == null || history.size() < 2) {
            return false;
        }

        // Check if last message is from user
        var lastMessage = history.get(history.size() - 1);
        return "user".equals(lastMessage.getRole());
    }

    private String routeFromBuildValidator(WorkflowState state) {
        if (state.getLastAgentDecision().getNextStep() == AgentDecision.NextStep.RETRY &&
                state.getBuildAttempt() < 3) {
            return "code_generator";
        }
        return state.getLastAgentDecision().getNextStep() == AgentDecision.NextStep.PROCEED ?
                "test_runner" : "ask_developer";
    }

    private String routeFromPRReviewer(WorkflowState state) {
        if (state.getLastAgentDecision().getNextStep() == AgentDecision.NextStep.RETRY &&
                state.getReviewAttempt() < 3) {
            return "code_generator";
        }
        return state.getLastAgentDecision().getNextStep() == AgentDecision.NextStep.PROCEED ?
                "readme_generator" : "ask_developer";
    }
}