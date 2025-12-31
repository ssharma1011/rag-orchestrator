package com.purchasingpower.autoflow.model.retrieval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * RetrievalPlan - LLM-generated plan for retrieving relevant code.
 *
 * Instead of hardcoded retrieval strategies, the LLM analyzes the question
 * and generates a dynamic plan with multiple retrieval strategies.
 *
 * Example:
 * Question: "Explain how authentication works across payment and user services"
 *
 * Generated Plan:
 * [
 *   {
 *     "type": "metadata_filter",
 *     "parameters": {"annotations": "@Secured,@RestController", "className_contains": "Auth"},
 *     "targetRepos": ["payment-service", "user-service"],
 *     "reasoning": "Need auth controllers in both services"
 *   },
 *   {
 *     "type": "graph_traversal",
 *     "parameters": {"start_node": "JwtTokenProvider", "relationship": "DEPENDS_ON", "max_depth": 3},
 *     "targetRepos": ["user-service"],
 *     "reasoning": "Need token generation dependencies"
 *   },
 *   {
 *     "type": "semantic_search",
 *     "parameters": {"query": "JWT token validation", "top_k": 10},
 *     "targetRepos": ["payment-service"],
 *     "reasoning": "Need token validation logic"
 *   }
 * ]
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalPlan implements Serializable {

    /**
     * List of retrieval strategies to execute in order.
     */
    @Builder.Default
    private List<RetrievalStrategy> strategies = new ArrayList<>();

    /**
     * Overall reasoning for this retrieval plan.
     */
    private String overallReasoning;

    /**
     * Estimated number of code chunks to retrieve.
     */
    private Integer estimatedChunks;

    /**
     * Add a strategy to the plan.
     */
    public void addStrategy(RetrievalStrategy strategy) {
        if (this.strategies == null) {
            this.strategies = new ArrayList<>();
        }
        this.strategies.add(strategy);
    }

    /**
     * Get total number of strategies in plan.
     */
    public int getStrategyCount() {
        return strategies != null ? strategies.size() : 0;
    }
}
