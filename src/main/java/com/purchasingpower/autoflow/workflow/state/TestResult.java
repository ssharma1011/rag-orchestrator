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
public class TestResult implements Serializable {

    private boolean allTestsPassed;

    private int testsPassed;
    private int testsFailed;
    private int testsSkipped;

    /**
     * CRITICAL: Don't use @Builder.Default - Jackson ignores it
     * Use custom getter to guarantee non-null
     */
    private List<String> failedTests;

    private double coverageBefore;
    private double coverageAfter;

    private String testLogs;

    private long durationMs;

    /**
     * Custom getter that GUARANTEES non-null list
     */
    public List<String> getFailedTests() {
        if (failedTests == null) {
            failedTests = new ArrayList<>();
        }
        return failedTests;
    }

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
        if (getFailedTests().isEmpty()) {
            return "All tests passed!";
        }
        return String.format("%d tests failed:\n%s",
                failedTests.size(),
                String.join("\n", failedTests)
        );
    }

    /**
     * Builder customization to ensure default values
     */
    public static class TestResultBuilder {
        // Ensure failedTests is never null when building
        public TestResult build() {
            if (failedTests == null) {
                failedTests = new ArrayList<>();
            }
            return new TestResult(
                    allTestsPassed,
                    testsPassed,
                    testsFailed,
                    testsSkipped,
                    failedTests,
                    coverageBefore,
                    coverageAfter,
                    testLogs,
                    durationMs
            );
        }
    }
}