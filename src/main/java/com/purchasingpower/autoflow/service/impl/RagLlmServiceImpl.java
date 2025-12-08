package com.purchasingpower.autoflow.service.impl;


import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.client.PineconeRetriever;
import com.purchasingpower.autoflow.model.llm.CodeGenerationResponse;
import com.purchasingpower.autoflow.service.RagLlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagLlmServiceImpl implements RagLlmService {

    private final GeminiClient geminiClient;
    private final PineconeRetriever pineconeRetriever;
    private final PineconeIngestServiceImpl pineconeIngestService;

    @Override
    public CodeGenerationResponse generatePatch(String requirements, String repoName, File workspaceDir) {
        log.info("Orchestrating AI Patch generation for: {}", repoName);

        boolean hasExistingCode = pineconeIngestService.ingestRepository(workspaceDir, repoName);

        String targetRepoForSearch;
        String augmentedRequirements = requirements;

        if (!hasExistingCode) {
            log.info("Repo is empty/new. Switching to GOLDEN TEMPLATE mode.");
            // Hardcoded name of your template in Pinecone
            targetRepoForSearch = "spring-boot-template";
            augmentedRequirements = "SCAFFOLD NEW PROJECT. " + requirements;
        } else {
            log.info("Repo has code. Using STANDARD RAG mode.");
            targetRepoForSearch = repoName;
        }

        // 1. Convert Text -> Vector
        List<Double> embedding = geminiClient.createEmbedding(requirements);

        // 2. Vector -> Context (RAG)
        String context = pineconeRetriever.findRelevantCode(embedding, targetRepoForSearch);

        // 3. Text + Context -> Code (LLM)
        return geminiClient.generateCodePlan(requirements, context);
    }
}