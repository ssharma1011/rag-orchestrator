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

/**
 * Orchestrates RAG (Retrieval-Augmented Generation) workflow:
 * 1. Index repository code using AST-based chunking
 * 2. Convert requirements to embedding vector
 * 3. Retrieve relevant code chunks from Pinecone
 * 4. Generate code patches using LLM with context
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagLlmServiceImpl implements RagLlmService {

    private final GeminiClient geminiClient;
    private final PineconeRetriever pineconeRetriever;
    private final PineconeIngestServiceImpl ingestService; // AST-based ingest service

    @Override
    public CodeGenerationResponse generatePatch(String requirements, String repoName, File workspaceDir) {
        log.info("Starting RAG workflow for repository: {}", repoName);

        // ======================================================================
        // STEP 1: INDEX REPOSITORY (AST-Based Chunking)
        // ======================================================================
        log.info("Step 1: Indexing repository code with AST parser...");
        boolean hasExistingCode = ingestService.ingestRepository(workspaceDir, repoName);

        String targetRepoForSearch;
        String augmentedRequirements = requirements;

        if (!hasExistingCode) {
            log.info("Repository is empty/new. Switching to SCAFFOLD mode.");
            // Use golden template for new projects
            targetRepoForSearch = "spring-boot-template";
            augmentedRequirements = "SCAFFOLD NEW PROJECT. " + requirements;
        } else {
            log.info("Repository has existing code. Using STANDARD RAG mode.");
            targetRepoForSearch = repoName;
        }

        // ======================================================================
        // STEP 2: EMBED REQUIREMENTS (Single Text)
        // ======================================================================
        log.info("Step 2: Creating embedding for requirements...");
        // Note: createEmbedding() now uses batch API internally (efficient for single text too)
        List<Double> requirementsEmbedding = geminiClient.createEmbedding(augmentedRequirements);

        log.debug("Generated embedding vector of dimension: {}", requirementsEmbedding.size());

        // ======================================================================
        // STEP 3: RETRIEVE RELEVANT CODE (Vector Search)
        // ======================================================================
        log.info("Step 3: Retrieving relevant code chunks from Pinecone...");
        String relevantContext = pineconeRetriever.findRelevantCode(
                requirementsEmbedding,
                targetRepoForSearch
        );

        if ("NO CONTEXT FOUND".equals(relevantContext) || relevantContext.isEmpty()) {
            log.warn("No relevant code found in vector database. LLM will work without context.");
            relevantContext = "No existing code found. Generate from scratch.";
        } else {
            log.info("Retrieved context: {} characters", relevantContext.length());
        }

        // ======================================================================
        // STEP 4: GENERATE CODE (LLM with Context)
        // ======================================================================
        log.info("Step 4: Generating code plan with Gemini...");
        CodeGenerationResponse codeGenResponse;

        if (hasExistingCode) {
            // Maintainer mode: modify existing code
            codeGenResponse = geminiClient.generateCodePlan(augmentedRequirements, relevantContext);
        } else {
            // Architect mode: scaffold new project
            codeGenResponse = geminiClient.generateScaffold(augmentedRequirements, repoName);
        }

        log.info("Code generation complete. Generated {} file edits and {} tests",
                codeGenResponse.getEdits() != null ? codeGenResponse.getEdits().size() : 0,
                codeGenResponse.getTestsAdded() != null ? codeGenResponse.getTestsAdded().size() : 0);

        return codeGenResponse;
    }
}