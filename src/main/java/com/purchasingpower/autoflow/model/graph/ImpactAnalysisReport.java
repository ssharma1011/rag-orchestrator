package com.purchasingpower.autoflow.model.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpactAnalysisReport {
    
    private String analyzedNode;
    private String repoName;
    
    private List<String> directDependencies;
    private List<String> transitiveDependencies;
    private List<String> directDependents;
    private List<String> transitiveDependents;
    private List<String> criticalPaths;
    
    private double impactScore;
    private RiskLevel riskLevel;
    
    public enum RiskLevel {
        LOW,      // < 5 dependents
        MEDIUM,   // 5-10 dependents
        HIGH,     // 10-20 dependents
        CRITICAL  // > 20 dependents
    }
    
    public String toMarkdown() {
        return String.format("""
            # Impact Analysis: %s
            
            ## Risk Assessment
            - **Risk Level**: %s
            - **Impact Score**: %.1f/10
            
            ## Dependencies
            - Direct: %d classes
            - Transitive: %d classes
            
            ## Dependents (Who uses this?)
            - Direct: %d classes
            - Transitive: %d classes
            
            ## Critical Paths
            %s
            
            ## Recommendation
            %s
            """,
            analyzedNode,
            riskLevel,
            impactScore,
            directDependencies.size(),
            transitiveDependencies.size(),
            directDependents.size(),
            transitiveDependents.size(),
            criticalPaths.isEmpty() ? "None" : String.join("\n", criticalPaths.stream().map(p -> "- " + p).toList()),
            getRecommendation()
        );
    }
    
    private String getRecommendation() {
        return switch (riskLevel) {
            case CRITICAL -> "⚠️ HIGH RISK: Changes will affect 20+ classes. Consider splitting this class or deprecating gradually.";
            case HIGH -> "⚠️ MODERATE RISK: Thoroughly test all dependent classes. Consider feature flags for rollback.";
            case MEDIUM -> "✓ MANAGEABLE: Standard testing should suffice. Monitor dependent classes.";
            case LOW -> "✓ LOW RISK: Few dependencies. Safe to modify with basic testing.";
        };
    }
}
