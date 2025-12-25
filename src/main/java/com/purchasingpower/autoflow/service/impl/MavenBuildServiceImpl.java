package com.purchasingpower.autoflow.service.impl;

import com.purchasingpower.autoflow.exception.BuildFailureException;
import com.purchasingpower.autoflow.service.MavenBuildService;
import com.purchasingpower.autoflow.workflow.state.BuildResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MavenBuildServiceImpl implements MavenBuildService {

    // Pattern to extract compilation errors from Maven output
    private static final Pattern ERROR_PATTERN = Pattern.compile("\\[ERROR\\]\\s+(.+)");

    @Override
    public BuildResult buildAndVerify(File projectDir) {
        log.info("Starting Maven Build & Verification in: {}", projectDir.getAbsolutePath());

        long startTime = System.currentTimeMillis();
        StringBuilder captureLogs = new StringBuilder();
        List<String> compilationErrors = new ArrayList<>();

        try {
            // 1. Determine OS command wrapper
            boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
            Process process = createMavenProcess(projectDir, isWindows);

            // 2. Stream logs in real-time and capture errors
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[MVN] {}", line);
                    captureLogs.append(line).append("\n");

                    // Extract compilation errors
                    Matcher matcher = ERROR_PATTERN.matcher(line);
                    if (matcher.find()) {
                        String error = matcher.group(1).trim();
                        // Filter out noise (Maven header errors)
                        if (!error.contains("Failed to execute goal") &&
                                !error.contains("To see the full stack trace")) {
                            compilationErrors.add(error);
                        }
                    }
                }
            }

            int exitCode = process.waitFor();
            long durationMs = System.currentTimeMillis() - startTime;

            if (exitCode != 0) {
                log.error("Maven Build FAILED with exit code: {} in {}ms", exitCode, durationMs);

                // CRITICAL FIX: Return BuildResult instead of throwing exception
                return BuildResult.builder()
                        .success(false)
                        .compilationErrors(compilationErrors.isEmpty()
                                ? List.of("Build failed with exit code " + exitCode)
                                : compilationErrors)
                        .buildLogs(captureLogs.toString())
                        .durationMs(durationMs)
                        .build();
            }

            log.info("✅ Maven Build & Tests PASSED in {}ms", durationMs);

            return BuildResult.builder()
                    .success(true)
                    .compilationErrors(new ArrayList<>())
                    .buildLogs(captureLogs.toString())
                    .durationMs(durationMs)
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Build interrupted", e);

            return BuildResult.builder()
                    .success(false)
                    .compilationErrors(List.of("Build interrupted: " + e.getMessage()))
                    .buildLogs(captureLogs.toString())
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("Build validation failed", e);

            return BuildResult.builder()
                    .success(false)
                    .compilationErrors(List.of("Build failed: " + e.getMessage()))
                    .buildLogs(captureLogs.toString())
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    private Process createMavenProcess(File projectDir, boolean isWindows) throws IOException {
        List<String> command = new ArrayList<>();

        if (isWindows) {
            command.add("cmd.exe");
            command.add("/c");
        } else {
            command.add("sh");
            command.add("-c");
        }

        // Maven command: -B (batch mode), clean install
        command.add("mvn -B clean install");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(projectDir);
        builder.redirectErrorStream(true); // Merge stderr into stdout

        return builder.start();
    }
}