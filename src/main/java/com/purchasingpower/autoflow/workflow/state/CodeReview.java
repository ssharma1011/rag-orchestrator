package com.purchasingpower.autoflow.workflow.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodeReview implements Serializable {

    private boolean approved;

    private List<ReviewIssue> issues;

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
     * Custom getter that GUARANTEES non-null list
     */
    public List<ReviewIssue> getIssues() {
        if (issues == null) {
            issues = new ArrayList<>();
        }
        return issues;
    }

    /**
     * Single review issue
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReviewIssue implements Serializable {
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
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StaticAnalysisResult implements Serializable {
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
        return getIssues().stream()
                .filter(i -> i.severity == ReviewIssue.Severity.CRITICAL)
                .count();
    }

    /**
     * Check if only minor issues
     */
    public boolean hasOnlyMinorIssues() {
        return getIssues().stream()
                .allMatch(i -> i.severity == ReviewIssue.Severity.LOW ||
                        i.severity == ReviewIssue.Severity.MEDIUM);
    }

    /**
     * Builder customization to ensure default values
     */
    public static class CodeReviewBuilder {
        public CodeReview build() {
            if (issues == null) issues = new ArrayList<>();

            return new CodeReview(
                    approved,
                    issues,
                    qualityScore,
                    summaryForDev,
                    staticAnalysis
            );
        }
    }
}