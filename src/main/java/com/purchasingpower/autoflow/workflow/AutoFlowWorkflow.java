package com.purchasingpower.autoflow.workflow;

import com.purchasingpower.autoflow.workflow.agents.*;
import com.purchasingpower.autoflow.workflow.state.AgentDecision;
import com.purchasingpower.autoflow.workflow.state.WorkflowState;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * Production-grade LangGraph4J workflow orchestration for AutoFlow.
 *
 * Orchestrates 10 agents with conditional routing, retry loops, and human-in-the-loop pauses.
 *
 * CRITICAL DESIGN:
 * - Uses knowledge graph (Oracle) for domain-based scope discovery
 * - Uses Pinecone vector embeddings for semantic code search
 * - Enables big application ingestion → comprehensive knowledge graph → best code generation
 *
 * @author AutoFlow Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoFlowWorkflow {

    // Inject all 10 agents
    private final RequirementAnalyzerAgent requirementAnalyzer;
    private final LogAnalyzerAgent logAnalyzer;
    private final CodeIndexerAgent codeIndexer;
    private final ScopeDiscoveryAgent scopeDiscovery;
    private final ContextBuilderAgent contextBuilder;
    private final CodeGeneratorAgent codeGenerator;
    private final BuildValidatorAgent buildValidator;
    private final TestRunnerAgent testRunner;
    private final PRReviewerAgent prReviewer;
    private final ReadmeGeneratorAgent readmeGenerator;
    private final PRCreatorAgent prCreator;

    private CompiledGraph<WorkflowState> compiledGraph;

    @PostConstruct
    public void initialize() {
        log.info("🚀 Initializing AutoFlow workflow graph with LangGraph4J...");

        StateGraph<WorkflowState> graph = new StateGraph<>(
                WorkflowState.SCHEMA,
                WorkflowState::new
        );

        // ================================================================
        // DEFINE ALL NODES (Agents)
        // ================================================================

        graph.addNode("requirement_analyzer", node_async(state ->
                executeAgent("RequirementAnalyzer", state, requirementAnalyzer::execute)
        ));

        graph.addNode("log_analyzer", node_async(state ->
                executeAgent("LogAnalyzer", state, logAnalyzer::execute)
        ));

        graph.addNode("code_indexer", node_async(state ->
                executeAgent("CodeIndexer", state, codeIndexer::execute)
        ));

        graph.addNode("scope_discovery", node_async(state ->
                executeAgent("ScopeDiscovery", state, scopeDiscovery::execute)
        ));

        graph.addNode("context_builder", node_async(state ->
                executeAgent("ContextBuilder", state, contextBuilder::execute)
        ));

        graph.addNode("code_generator", node_async(state ->
                executeAgent("CodeGenerator", state, codeGenerator::execute)
        ));

        graph.addNode("build_validator", node_async(state ->
                executeAgent("BuildValidator", state, buildValidator::execute)
        ));

        graph.addNode("test_runner", node_async(state ->
                executeAgent("TestRunner", state, testRunner::execute)
        ));

        graph.addNode("pr_reviewer", node_async(state ->
                executeAgent("PRReviewer", state, prReviewer::execute)
        ));

        graph.addNode("readme_generator", node_async(state ->
                executeAgent("ReadmeGenerator", state, readmeGenerator::execute)
        ));

        graph.addNode("pr_creator", node_async(state ->
                executeAgent("PRCreator", state, prCreator::execute)
        ));

        // Human-in-the-loop pause node
        graph.addNode("ask_developer", node_async(state -> {
            log.info("📍 PAUSING for human input...");
            state.setWorkflowStatus("PAUSED");
            return CompletableFuture.completedFuture(state.toMap());
        }));

        // ================================================================
        // DEFINE EDGES (Routing Logic)
        // ================================================================

        // Start → RequirementAnalyzer
        graph.addEdge(START, "requirement_analyzer");

        // RequirementAnalyzer → LogAnalyzer or CodeIndexer
        graph.addConditionalEdges(
                "requirement_analyzer",
                edge_async(state -> {
                    if (shouldPause(state)) return "ask_developer";
                    return state.hasLogs() ? "log_analyzer" : "code_indexer";
                }),
                Map.of(
                        "log_analyzer", "log_analyzer",
                        "code_indexer", "code_indexer",
                        "ask_developer", "ask_developer"
                )
        );

        // LogAnalyzer → CodeIndexer
        graph.addEdge("log_analyzer", "code_indexer");

        // CodeIndexer → ScopeDiscovery or AskDev
        graph.addConditionalEdges(
                "code_indexer",
                edge_async(state -> shouldPause(state) ? "ask_developer" : "scope_discovery"),
                Map.of(
                        "scope_discovery", "scope_discovery",
                        "ask_developer", "ask_developer"
                )
        );

        // ScopeDiscovery → ContextBuilder or AskDev
        graph.addConditionalEdges(
                "scope_discovery",
                edge_async(state -> shouldPause(state) ? "ask_developer" : "context_builder"),
                Map.of(
                        "context_builder", "context_builder",
                        "ask_developer", "ask_developer"
                )
        );

        // ContextBuilder → CodeGenerator or AskDev
        graph.addConditionalEdges(
                "context_builder",
                edge_async(state -> shouldPause(state) ? "ask_developer" : "code_generator"),
                Map.of(
                        "code_generator", "code_generator",
                        "ask_developer", "ask_developer"
                )
        );

        // CodeGenerator → BuildValidator
        graph.addEdge("code_generator", "build_validator");

        // BuildValidator → TestRunner, CodeGenerator (retry), or AskDev
        graph.addConditionalEdges(
                "build_validator",
                edge_async(this::routeFromBuildValidator),
                Map.of(
                        "test_runner", "test_runner",
                        "code_generator", "code_generator",  // Retry loop
                        "ask_developer", "ask_developer"
                )
        );

        // TestRunner → PRReviewer or AskDev
        graph.addConditionalEdges(
                "test_runner",
                edge_async(state -> shouldPause(state) ? "ask_developer" : "pr_reviewer"),
                Map.of(
                        "pr_reviewer", "pr_reviewer",
                        "ask_developer", "ask_developer"
                )
        );

        // PRReviewer → ReadmeGenerator, CodeGenerator (retry), or AskDev
        graph.addConditionalEdges(
                "pr_reviewer",
                edge_async(this::routeFromPRReviewer),
                Map.of(
                        "readme_generator", "readme_generator",
                        "code_generator", "code_generator",  // Retry loop
                        "ask_developer", "ask_developer"
                )
        );

        // ReadmeGenerator → PRCreator
        graph.addEdge("readme_generator", "pr_creator");

        // PRCreator → END
        graph.addEdge("pr_creator", END);

        // Compile graph
        this.compiledGraph = graph.compile();

        log.info("✅ AutoFlow workflow graph initialized successfully");
    }

    // ================================================================
    // HELPER METHODS
    // ================================================================

    /**
     * Execute an agent and update state.
     * Returns Map<String, Object> as required by LangGraph4J.
     */
    private CompletableFuture<Map<String, Object>> executeAgent(
            String agentName,
            WorkflowState state,
            java.util.function.Function<WorkflowState, AgentDecision> agentFunction) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("📍 Executing: {}", agentName);
                state.setCurrentAgent(agentName);

                AgentDecision decision = agentFunction.apply(state);
                state.setLastAgentDecision(decision);

                log.info("✅ {} completed: {}", agentName, decision.getNextStep());

                return state.toMap();

            } catch (Exception e) {
                log.error("❌ {} failed", agentName, e);
                state.setLastAgentDecision(AgentDecision.error(e.getMessage()));
                return state.toMap();
            }
        });
    }

    /**
     * Check if workflow should pause for human input.
     */
    private boolean shouldPause(WorkflowState state) {
        if (state.getLastAgentDecision() == null) {
            return false;
        }
        return state.getLastAgentDecision().getNextStep() == AgentDecision.NextStep.ASK_DEV;
    }

    /**
     * Route from BuildValidator.
     * Implements retry loop: build fails → retry CodeGenerator (max 3 attempts)
     */
    private String routeFromBuildValidator(WorkflowState state) {
        AgentDecision decision = state.getLastAgentDecision();

        if (decision.getNextStep() == AgentDecision.NextStep.PROCEED) {
            return "test_runner";
        }

        if (decision.getNextStep() == AgentDecision.NextStep.RETRY) {
            if (state.getBuildAttempt() < 3) {
                state.setBuildAttempt(state.getBuildAttempt() + 1);
                log.info("🔄 Build retry #{}", state.getBuildAttempt());
                return "code_generator";
            } else {
                log.warn("⚠️ Build failed after 3 attempts, asking developer");
                return "ask_developer";
            }
        }

        return "ask_developer";
    }

    /**
     * Route from PRReviewer.
     * Implements retry loop: critical issues → retry CodeGenerator (max 3 attempts)
     */
    private String routeFromPRReviewer(WorkflowState state) {
        AgentDecision decision = state.getLastAgentDecision();

        if (decision.getNextStep() == AgentDecision.NextStep.PROCEED) {
            return "readme_generator";
        }

        if (decision.getNextStep() == AgentDecision.NextStep.RETRY) {
            if (state.getReviewAttempt() < 3) {
                state.setReviewAttempt(state.getReviewAttempt() + 1);
                log.info("🔄 Review retry #{}", state.getReviewAttempt());
                return "code_generator";
            } else {
                log.warn("⚠️ Review failed after 3 attempts, asking developer");
                return "ask_developer";
            }
        }

        return "ask_developer";
    }

    // ================================================================
    // PUBLIC API
    // ================================================================

    /**
     * Execute workflow from start.
     * Runs until completion or pause.
     */
    public WorkflowState execute(WorkflowState initialState) {
        log.info("🚀 Starting workflow: {}", initialState.getConversationId());

        initialState.setWorkflowStatus("RUNNING");
        initialState.setBuildAttempt(0);
        initialState.setReviewAttempt(0);

        try {
            Map<String, Object> result = compiledGraph.invoke(initialState.toMap());
            return WorkflowState.fromMap(result);

        } catch (Exception e) {
            log.error("Workflow execution failed", e);
            initialState.setWorkflowStatus("FAILED");
            initialState.setLastAgentDecision(AgentDecision.error(e.getMessage()));
            return initialState;
        }
    }

    /**
     * Get compiled graph (for debugging/visualization).
     */
    public CompiledGraph<WorkflowState> getCompiledGraph() {
        return compiledGraph;
    }
}
