package com.purchasingpower.autoflow.service.impl;

import com.purchasingpower.autoflow.model.llm.CodeGenerationResponse;
import com.purchasingpower.autoflow.model.llm.FileEdit;
import com.purchasingpower.autoflow.model.llm.TestFile;
import com.purchasingpower.autoflow.service.FilePatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

@Slf4j
@Service
public class FilePatchServiceImpl implements FilePatchService {

    @Override
    public void applyChanges(File workspaceDir, CodeGenerationResponse plan) throws IOException {
        log.info("Applying patches to workspace: {}", workspaceDir.getAbsolutePath());

        // 1. Apply Source Code Edits
        if (plan.getEdits() != null) {
            for (FileEdit edit : plan.getEdits()) {

                if (edit.getPath() == null || edit.getPath().isBlank()) {
                    log.warn("Skipping file edit: Path is null or empty in LLM response.");
                    continue;
                }

                File targetFile = new File(workspaceDir, edit.getPath());

                // Handle deletion
                if ("delete".equalsIgnoreCase(edit.getOp())) { // Using string "delete" from JSON
                    if (targetFile.delete()) {
                        log.info("Deleted file: {}", edit.getPath());
                    } else {
                        log.warn("Failed to delete file (may not exist): {}", edit.getPath());
                    }
                    continue;
                }

                // Handle create/modify
                writeFile(targetFile, edit.getContent());
                log.debug("Applied edit to: {}", edit.getPath());
            }
        }
        else {
            log.warn("LLM returned no 'edits' array. Continuing.");
        }


        // 2. Apply New Tests
        if (plan.getTestsAdded() != null) {
            for (TestFile test : plan.getTestsAdded()) {
                if (test.getPath() == null || test.getPath().isBlank()) {
                    log.warn("Skipping test file: Path is null or empty in LLM response.");
                    continue;
                }
                File targetFile = new File(workspaceDir, test.getPath());
                writeFile(targetFile, test.getContent());
                log.info("Created test file: {}", test.getPath());
            }
        } else {
            log.warn("LLM returned no 'tests_added' array. Continuing.");
        }
    }

    private void writeFile(File file, String content) throws IOException {
        // Ensure parent directories exist (e.g., src/main/java/com/app/...)
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        // Write content (Overwrite if exists)
        Files.writeString(
                file.toPath(),
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }
}