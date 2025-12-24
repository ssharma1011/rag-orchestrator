package com.purchasingpower.autoflow.workflow.agents;

import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestRunnerAgent {

    public Map<String, Object> execute(WorkflowState state) {
        log.info("🧪 Running tests...");

        try {
            TestResult result = runTests(state);
            
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("testResult", result);

            // FIX: Use isAllTestsPassed() and compute total
            int totalTests = result.getTestsPassed() + result.getTestsFailed();
            
            if (result.isAllTestsPassed()) {
                log.info("✅ All tests passed! ({} total)", totalTests);
                updates.put("lastAgentDecision", AgentDecision.proceed("All tests passed"));
                return updates;
            }

            // FIX: Use failedTests (List<String>)
            log.warn("⚠️ Some tests failed: {} passed, {} failed",
                    result.getTestsPassed(), result.getTestsFailed());

            updates.put("lastAgentDecision", AgentDecision.askDev(
                "⚠️ **Test Failures**\n\n" +
                "Passed: " + result.getTestsPassed() + "\n" +
                "Failed: " + result.getTestsFailed() + "\n\n" +
                "Failed Tests:\n" + String.join("\n", result.getFailedTests()) + "\n\n" +
                "Proceed to PR review anyway?"
            ));
            return updates;

        } catch (Exception e) {
            log.error("Test execution failed", e);
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("lastAgentDecision", AgentDecision.error("Test execution error: " + e.getMessage()));
            return updates;
        }
    }

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

        command.add("mvn -B test");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(state.getWorkspaceDir());
        builder.redirectErrorStream(true);

        Process process = builder.start();

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

        return parseTestResults(logs.toString(), exitCode == 0, duration);
    }

    private TestResult parseTestResults(String logs, boolean success, long duration) {
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        List<String> failedTests = new ArrayList<>();

        String[] lines = logs.split("\n");
        for (String line : lines) {
            if (line.contains("Tests run:")) {
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
            
            if (line.contains("<<< FAILURE!") || line.contains("<<< ERROR!")) {
                String testName = line.substring(0, line.indexOf("(")).trim();
                failedTests.add(testName);
            }
        }

        return TestResult.builder()
                .allTestsPassed(failed == 0)
                .testsPassed(passed - failed)
                .testsFailed(failed)
                .testsSkipped(skipped)
                .failedTests(failedTests)
                .testLogs(logs)
                .durationMs(duration)
                .coverageBefore(0)
                .coverageAfter(0)
                .build();
    }

    private int extractNumber(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
