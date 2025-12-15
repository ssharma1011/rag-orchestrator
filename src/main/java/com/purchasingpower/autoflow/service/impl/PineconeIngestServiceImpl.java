package com.purchasingpower.autoflow.service.impl;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.configuration.AppProperties;
import com.purchasingpower.autoflow.model.ast.CodeChunk;
import com.purchasingpower.autoflow.service.AstParserService;
import com.purchasingpower.autoflow.service.PineconeIngestService;
import io.pinecone.clients.Pinecone;
import io.pinecone.unsigned_indices_model.VectorWithUnsignedIndices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * AST-based repository ingestion service.
 * Replaces text-based embedding with hierarchical class/method chunking.
 *
 * Performance: Uses batch embedding API for 100x speed improvement.
 */
@Slf4j
@Service
public class PineconeIngestServiceImpl implements PineconeIngestService {

    private final AstParserService astParser;
    private final GeminiClient geminiClient;
    private final Pinecone pineconeClient;
    private final String indexName;

    public PineconeIngestServiceImpl(
            AstParserService astParser,
            GeminiClient geminiClient,
            AppProperties props) {
        this.astParser = astParser;
        this.geminiClient = geminiClient;
        this.pineconeClient = new Pinecone.Builder(props.getPinecone().getApiKey()).build();
        this.indexName = props.getPinecone().getIndexName();
    }

    @Override
    public boolean ingestRepository(File workspaceDir, String repoName) {
        log.info("Starting AST-based ingestion for repository: {}", repoName);

        // ======================================================================
        // STEP 1: DETECT IF PROJECT IS EMPTY (Scaffold Detection)
        // ======================================================================
        File pom = new File(workspaceDir, "pom.xml");
        if (!pom.exists()) {
            log.info("No pom.xml found. Treating as NEW/EMPTY project (scaffold mode).");
            return false;
        }

        List<File> javaFiles = findJavaFiles(workspaceDir);

        if (javaFiles.isEmpty()) {
            log.info("No Java source files found. Treating as NEW/EMPTY project.");
            return false;
        }

        log.info("Found {} Java files to process", javaFiles.size());

        // ======================================================================
        // STEP 2: PARSE ALL FILES INTO CODE CHUNKS (AST)
        // ======================================================================
        log.info("Step 2: Parsing Java files with AST parser...");
        List<CodeChunk> allChunks = new ArrayList<>();

        for (File javaFile : javaFiles) {
            try {
                List<CodeChunk> fileChunks = astParser.parseJavaFile(javaFile, repoName);
                allChunks.addAll(fileChunks);

                log.debug("Parsed {}: {} chunks", javaFile.getName(), fileChunks.size());

            } catch (Exception e) {
                log.warn("Failed to parse {}: {}", javaFile.getName(), e.getMessage());
            }
        }

        if (allChunks.isEmpty()) {
            log.warn("No valid code chunks extracted. Skipping ingestion.");
            return false;
        }

        log.info("Extracted {} total code chunks (classes + methods)", allChunks.size());

        // ======================================================================
        // STEP 3: BATCH EMBED ALL CHUNKS (Fast!)
        // ======================================================================
        log.info("Step 3: Creating embeddings using batch API...");

        // Collect all texts to embed
        List<String> textsToEmbed = allChunks.stream()
                .map(CodeChunk::getContent)
                .toList();

        // Batch embed (100x faster than loop)
        List<List<Double>> embeddings = geminiClient.batchCreateEmbeddings(textsToEmbed);

        if (embeddings.size() != allChunks.size()) {
            log.error("Embedding count mismatch! Expected {}, got {}",
                    allChunks.size(), embeddings.size());
            throw new RuntimeException("Batch embedding failed: size mismatch");
        }

        log.info("Created {} embeddings successfully", embeddings.size());

        // ======================================================================
        // STEP 4: BUILD PINECONE VECTORS
        // ======================================================================
        log.info("Step 4: Building Pinecone vectors...");
        List<VectorWithUnsignedIndices> vectors = new ArrayList<>();

        for (int i = 0; i < allChunks.size(); i++) {
            CodeChunk chunk = allChunks.get(i);
            List<Double> embedding = embeddings.get(i);

            VectorWithUnsignedIndices vector = createVector(chunk, embedding);
            vectors.add(vector);
        }

        // ======================================================================
        // STEP 5: UPSERT TO PINECONE (Batch)
        // ======================================================================
        log.info("Step 5: Upserting {} vectors to Pinecone index '{}'...", vectors.size(), indexName);

        try {
            // Upsert in batches of 100 (Pinecone limit)
            int batchSize = 100;
            for (int i = 0; i < vectors.size(); i += batchSize) {
                int end = Math.min(i + batchSize, vectors.size());
                List<VectorWithUnsignedIndices> batch = vectors.subList(i, end);

                pineconeClient.getIndexConnection(indexName).upsert(batch, "");

                log.debug("Upserted batch {}-{} of {}", i, end, vectors.size());
            }

            log.info("✅ Successfully indexed {} code chunks for {}", vectors.size(), repoName);
            return true;

        } catch (Exception e) {
            log.error("Failed to upsert vectors to Pinecone", e);
            throw new RuntimeException("Pinecone upsert failed", e);
        }
    }

    /**
     * Converts a CodeChunk + embedding into a Pinecone vector.
     */
    private VectorWithUnsignedIndices createVector(CodeChunk chunk, List<Double> embedding) {
        // Convert Double to Float (Pinecone uses float32)
        List<Float> floatVector = embedding.stream()
                .map(Double::floatValue)
                .toList();

        // Flatten metadata for Pinecone storage
        Map<String, String> flatMetadata = chunk.toFlatMetadata();

        // Build Protobuf Struct for Pinecone
        Struct.Builder metadataBuilder = Struct.newBuilder();
        for (Map.Entry<String, String> entry : flatMetadata.entrySet()) {
            metadataBuilder.putFields(
                    entry.getKey(),
                    Value.newBuilder().setStringValue(entry.getValue()).build()
            );
        }

        return new VectorWithUnsignedIndices(
                chunk.getId(),           // Vector ID (e.g., "payment-service:PaymentService.processPayment")
                floatVector,             // Embedding vector
                metadataBuilder.build(), // Metadata (searchable fields)
                null                     // Sparse values (not used)
        );
    }

    /**
     * Recursively finds all Java source files (excludes test files).
     */
    private List<File> findJavaFiles(File root) {
        try (Stream<Path> walk = Files.walk(root.toPath())) {
            return walk
                    .filter(p -> !Files.isDirectory(p))
                    .map(Path::toFile)
                    .filter(f -> f.getName().endsWith(".java"))
                    .filter(f -> !f.getAbsolutePath().contains("src" + File.separator + "test"))
                    .toList();
        } catch (IOException e) {
            log.error("Failed to walk directory: {}", root, e);
            return new ArrayList<>();
        }
    }
}