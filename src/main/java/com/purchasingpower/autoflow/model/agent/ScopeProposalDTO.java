package com.purchasingpower.autoflow.model.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object for scope proposal from LLM.
 *
 * Represents the raw JSON response from the LLM when analyzing
 * scope for a requirement. This DTO is then converted to the
 * domain model ScopeProposal.
 *
 * @see com.purchasingpower.autoflow.workflow.state.ScopeProposal
 * @see com.purchasingpower.autoflow.workflow.agents.ScopeDiscoveryAgent
 */
public class ScopeProposalDTO {
    public List<String> filesToModify = new ArrayList<>();
    public List<String> filesToCreate = new ArrayList<>();
    public List<String> testsToUpdate = new ArrayList<>();
    public String reasoning;
    public int estimatedComplexity;
    public List<String> risks;
}
