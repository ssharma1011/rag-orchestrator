package com.purchasingpower.autoflow.workflow.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of running tests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestResult {

    private boolean allTestsPassed;

    private int testsPassed;
    private int testsFailed;
    private int testsSkipped;

    @Builder.Default
    private List<String> failedTests = new ArrayList<>();

    private double coverageBefore;
    private double coverageAfter;

    private String testLogs;

    private long durationMs;

    /**
     * Coverage delta (positive = improvement)
     */
    public double getCoverageDelta() {
        return coverageAfter - coverageBefore;
    }

    /**
     * Formatted failure message
     */
    public String getFailuresSummary() {
        if (failedTests.isEmpty()) {
            return "All tests passed!";
        }
        return String.format("%d tests failed:\n%s",
                failedTests.size(),
                String.join("\n", failedTests)
        );
    }
}