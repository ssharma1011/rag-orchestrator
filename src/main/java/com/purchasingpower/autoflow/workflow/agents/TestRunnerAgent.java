package com.purchasingpower.autoflow.workflow.agents;

import com.purchasingpower.autoflow.service.MavenBuildService;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * AGENT 7: Test Runner
 *
 * Purpose: Run tests and check coverage
 *
 * REUSES existing MavenBuildService for test execution!
 *
 * If tests fail:
 * - Shows to developer (not automatic retry)
 * - Dev can approve anyway or request fix
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestRunnerAgent {

    public AgentDecision execute(WorkflowState state) {
        log.info("🧪 Running tests...");

        try {
            // Run tests with Maven
            long startTime = System.currentTimeMillis();
            TestResult result = runTests(state);
            state.setTestResult(result);

            log.info("✅ Tests complete. Passed: {}/{}, Coverage: {}%",
                    result.getTestsPassed(),
                    result.getTestsPassed() + result.getTestsFailed(),
                    result.getCoverageAfter());

            // If tests failed, ask dev
            if (!result.isAllTestsPassed()) {
                return AgentDecision.askDev(
                        "⚠️ **Some Tests Failed**\n\n" +
                                result.getFailuresSummary() + "\n\n" +
                                "Options:\n" +
                                "1. Proceed anyway (risky)\n" +
                                "2. Fix tests first\n" +
                                "3. Show me the test logs"
                );
            }

            // Check coverage delta
            if (result.getCoverageDelta() < 0) {
                return AgentDecision.askDev(
                        "⚠️ **Coverage Decreased**\n\n" +
                                String.format("Coverage dropped by %.1f%%\n",
                                        Math.abs(result.getCoverageDelta())) +
                                "Before: " + result.getCoverageBefore() + "%\n" +
                                "After: " + result.getCoverageAfter() + "%\n\n" +
                                "Proceed anyway?"
                );
            }

            return AgentDecision.proceed("All tests passed, proceeding to PR review");

        } catch (Exception e) {
            log.error("Test execution failed", e);
            return AgentDecision.error("Test execution error: " + e.getMessage());
        }
    }

    /**
     * Run Maven tests and parse results
     */
    private TestResult runTests(WorkflowState state) throws Exception {
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

        List<String> command = new ArrayList<>();
        if (isWindows) {
            command.add("cmd.exe");
            command.add("/c");
        } else {
            command.add("sh");
            command.add("-c");
        }

        // Run tests only (not full build)
        command.add("mvn -B test");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(state.getWorkspaceDir());
        builder.redirectErrorStream(true);

        Process process = builder.start();

        // Capture output
        StringBuilder logs = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("[MVN] {}", line);
                logs.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        long duration = System.currentTimeMillis();

        // Parse test results from logs
        return parseTestResults(logs.toString(), exitCode == 0, duration);
    }

    /**
     * Parse Maven test output
     */
    private TestResult parseTestResults(String logs, boolean success, long duration) {
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        List<String> failedTests = new ArrayList<>();

        // Parse Maven output
        // Look for: "Tests run: X, Failures: Y, Errors: Z, Skipped: W"
        String[] lines = logs.split("\n");
        for (String line : lines) {
            if (line.contains("Tests run:")) {
                // Example: Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
                String[] parts = line.split(",");
                for (String part : parts) {
                    if (part.contains("Tests run:")) {
                        passed = extractNumber(part);
                    } else if (part.contains("Failures:")) {
                        failed += extractNumber(part);
                    } else if (part.contains("Errors:")) {
                        failed += extractNumber(part);
                    } else if (part.contains("Skipped:")) {
                        skipped = extractNumber(part);
                    }
                }
            }

            // Capture failed test names
            if (line.contains("FAILED") || line.contains("ERROR")) {
                failedTests.add(line.trim());
            }
        }

        // TODO: Parse coverage (requires JaCoCo report)
        double coverageBefore = 0.0;
        double coverageAfter = 0.0;

        return TestResult.builder()
                .allTestsPassed(success && failed == 0)
                .testsPassed(passed - failed)
                .testsFailed(failed)
                .testsSkipped(skipped)
                .failedTests(failedTests)
                .coverageBefore(coverageBefore)
                .coverageAfter(coverageAfter)
                .testLogs(logs)
                .durationMs(duration)
                .build();
    }

    private int extractNumber(String text) {
        try {
            String num = text.replaceAll("[^0-9]", "");
            return Integer.parseInt(num);
        } catch (Exception e) {
            return 0;
        }
    }
}