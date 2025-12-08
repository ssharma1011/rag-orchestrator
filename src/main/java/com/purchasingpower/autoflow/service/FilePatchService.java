package com.purchasingpower.autoflow.service;

import com.purchasingpower.autoflow.model.llm.CodeGenerationResponse;

import java.io.File;
import java.io.IOException;

public interface FilePatchService {
    void applyChanges(File workspaceDir, CodeGenerationResponse plan) throws IOException;
}
