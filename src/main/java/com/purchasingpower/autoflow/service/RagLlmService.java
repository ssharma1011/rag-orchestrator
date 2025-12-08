package com.purchasingpower.autoflow.service;

import com.purchasingpower.autoflow.model.llm.CodeGenerationResponse;

import java.io.File;

public interface RagLlmService {
    CodeGenerationResponse generatePatch(String requirements, String repoName, File workspaceDir);
}
