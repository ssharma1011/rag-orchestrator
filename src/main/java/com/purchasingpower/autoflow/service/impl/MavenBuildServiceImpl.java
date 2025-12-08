package com.purchasingpower.autoflow.service.impl;

import com.purchasingpower.autoflow.exception.BuildFailureException;
import com.purchasingpower.autoflow.service.MavenBuildService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class MavenBuildServiceImpl implements MavenBuildService {

    @Override
    public void buildAndVerify(File projectDir) {
        log.info("Starting Maven Build & Verification in: {}", projectDir.getAbsolutePath());
        StringBuilder captureLogs = new StringBuilder(); // Store logs here

        try {
            // 1. Determine OS command wrapper
            boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
            Process process = getProcess(projectDir, isWindows);

            // 3. Stream logs in real-time so you can see compilation errors
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[MVN] {}", line); // Log as debug to avoid spamming, or info to see progress
                    captureLogs.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("Maven Build FAILED with exit code: {}", exitCode);
                throw new BuildFailureException("Compilation Failed", captureLogs.toString());
            }

            log.info("✅ Maven Build & Tests PASSED.");

        } catch (BuildFailureException e) {
            throw new BuildFailureException("Compilation Failed", captureLogs.toString());
        }
        catch (Exception e) {
            throw new RuntimeException("Build validation failed: " + e.getMessage(), e);
        }
    }

    private static Process getProcess(File projectDir, boolean isWindows) throws IOException {
        List<String> command = new ArrayList<>();

        if (isWindows) {
            command.add("cmd.exe");
            command.add("/c");
        } else {
            command.add("sh");
            command.add("-c");
        }

        // 2. The Maven Command
        // -B: Batch mode (no colors/interactive)
        // clean install: Compiles and runs Tests
        command.add("mvn -B clean install");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(projectDir);
        builder.redirectErrorStream(true); // Merge stderr into stdout

        Process process = builder.start();
        return process;
    }
}
