package com.purchasingpower.autoflow.workflow;

import com.purchasingpower.autoflow.workflow.agents.*;
import com.purchasingpower.autoflow.workflow.state.AgentDecision;
import com.purchasingpower.autoflow.workflow.state.WorkflowState;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoFlowWorkflow {

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
    public void initialize() throws GraphStateException {
        log.info("🚀 Initializing AutoFlow workflow graph...");
        StateGraph<WorkflowState> graph = new StateGraph<>(WorkflowState::new);

        // Define Nodes
        graph.addNode("requirement_analyzer", node_async(s -> executeAgent("RequirementAnalyzer", s, requirementAnalyzer::execute)));
        graph.addNode("log_analyzer", node_async(s -> executeAgent("LogAnalyzer", s, logAnalyzer::execute)));
        graph.addNode("code_indexer", node_async(s -> executeAgent("CodeIndexer", s, codeIndexer::execute)));
        graph.addNode("scope_discovery", node_async(s -> executeAgent("ScopeDiscovery", s, scopeDiscovery::execute)));
        graph.addNode("context_builder", node_async(s -> executeAgent("ContextBuilder", s, contextBuilder::execute)));
        graph.addNode("code_generator", node_async(s -> executeAgent("CodeGenerator", s, codeGenerator::execute)));
        graph.addNode("build_validator", node_async(s -> executeAgent("BuildValidator", s, buildValidator::execute)));
        graph.addNode("test_runner", node_async(s -> executeAgent("TestRunner", s, testRunner::execute)));
        graph.addNode("pr_reviewer", node_async(s -> executeAgent("PRReviewer", s, prReviewer::execute)));
        graph.addNode("readme_generator", node_async(s -> executeAgent("ReadmeGenerator", s, readmeGenerator::execute)));
        graph.addNode("pr_creator", node_async(s -> executeAgent("PRCreator", s, prCreator::execute)));

        graph.addNode("ask_developer", node_async(s -> {
            s.setWorkflowStatus("PAUSED");
            return s.toMap();
        }));

        // Define Edges
        graph.addEdge(START, "requirement_analyzer");

        graph.addConditionalEdges("requirement_analyzer",
                edge_async(s -> shouldPause(s) ? "ask_developer" : (s.hasLogs() ? "log_analyzer" : "code_indexer")),
                Map.of("log_analyzer", "log_analyzer", "code_indexer", "code_indexer", "ask_developer", "ask_developer"));

        graph.addEdge("log_analyzer", "code_indexer");

        graph.addConditionalEdges("code_indexer",
                edge_async(s -> shouldPause(s) ? "ask_developer" : "scope_discovery"),
                Map.of("scope_discovery", "scope_discovery", "ask_developer", "ask_developer"));

        graph.addConditionalEdges("scope_discovery",
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

        this.compiledGraph = graph.compile();
    }

    public WorkflowState execute(WorkflowState initialState) {
        log.info("🚀 Starting workflow: {}", initialState.getConversationId());
        initialState.setWorkflowStatus("RUNNING");
        initialState.setBuildAttempt(0);
        initialState.setReviewAttempt(0);

        try {
            Optional<WorkflowState> result = compiledGraph.invoke(initialState.toMap());

            // If the Optional is empty, return original state, else return result
            return result.orElse(initialState);

        } catch (Exception e) {
            log.error("Workflow execution failed", e);
            initialState.setWorkflowStatus("FAILED");
            initialState.setLastAgentDecision(AgentDecision.error(e.getMessage()));
            return initialState;
        }
    }

    private Map<String, Object> executeAgent(String name, WorkflowState state, java.util.function.Function<WorkflowState, AgentDecision> func) {
        try {
            log.info("📍 Executing: {}", name);
            state.setCurrentAgent(name);
            AgentDecision decision = func.apply(state);
            state.setLastAgentDecision(decision);
            return state.toMap();
        } catch (Exception e) {
            log.error("❌ {} failed", name, e);
            state.setLastAgentDecision(AgentDecision.error(e.getMessage()));
            return state.toMap();
        }
    }

    private boolean shouldPause(WorkflowState state) {
        return state.getLastAgentDecision() != null && state.getLastAgentDecision().getNextStep() == AgentDecision.NextStep.ASK_DEV;
    }

    private String routeFromBuildValidator(WorkflowState state) {
        if (state.getLastAgentDecision().getNextStep() == AgentDecision.NextStep.RETRY && state.getBuildAttempt() < 3) {
            state.incrementBuildAttempt();
            return "code_generator";
        }
        return state.getLastAgentDecision().getNextStep() == AgentDecision.NextStep.PROCEED ? "test_runner" : "ask_developer";
    }

    private String routeFromPRReviewer(WorkflowState state) {
        if (state.getLastAgentDecision().getNextStep() == AgentDecision.NextStep.RETRY && state.getReviewAttempt() < 3) {
            state.incrementReviewAttempt();
            return "code_generator";
        }
        return state.getLastAgentDecision().getNextStep() == AgentDecision.NextStep.PROCEED ? "readme_generator" : "ask_developer";
    }
}