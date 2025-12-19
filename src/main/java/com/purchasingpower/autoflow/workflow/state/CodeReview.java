package com.purchasingpower.autoflow.workflow.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of PR review by agent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeReview {

    private boolean approved;

    @Builder.Default
    private List<ReviewIssue> issues = new ArrayList<>();

    /**
     * Overall quality score (0.0 - 1.0)
     */
    private double qualityScore;

    /**
     * Summary for developer
     */
    private String summaryForDev;

    /**
     * Static analysis results
     */
    private StaticAnalysisResult staticAnalysis;

    /**
     * Single review issue
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewIssue {
        private Severity severity;
        private String category;  // "security", "quality", "test", "architecture"
        private String file;
        private Integer line;
        private String description;
        private String suggestion;

        public enum Severity {
            CRITICAL,  // Must fix
            HIGH,      // Should fix
            MEDIUM,    // Nice to fix
            LOW        // Optional
        }
    }

    /**
     * Static analysis results
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaticAnalysisResult {
        private int checkstyleViolations;
        private int spotbugsIssues;
        private int pmdWarnings;
        private int securityIssues;
        private double codeComplexity;  // Average cyclomatic complexity
    }

    /**
     * Count critical issues
     */
    public long getCriticalIssueCount() {
        return issues.stream()
                .filter(i -> i.severity == ReviewIssue.Severity.CRITICAL)
                .count();
    }

    /**
     * Check if only minor issues
     */
    public boolean hasOnlyMinorIssues() {
        return issues.stream()
                .allMatch(i -> i.severity == ReviewIssue.Severity.LOW ||
                        i.severity == ReviewIssue.Severity.MEDIUM);
    }
}