package com.purchasingpower.autoflow.service.impl;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.purchasingpower.autoflow.client.GeminiClient;
import com.purchasingpower.autoflow.configuration.AppProperties;
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
import java.util.stream.Stream;

@Slf4j
@Service
public class PineconeIngestServiceImpl implements PineconeIngestService {

    private final GeminiClient geminiClient;
    private final Pinecone pineconeClient;
    private final String indexName;

    public PineconeIngestServiceImpl(GeminiClient geminiClient, AppProperties props) {
        this.geminiClient = geminiClient;
        this.pineconeClient = new Pinecone.Builder(props.getPinecone().getApiKey()).build();
        this.indexName = props.getPinecone().getIndexName();
    }

    @Override
    public boolean ingestRepository(File workspaceDir, String repoName) {
        // ✅ FIX 1: Strict Scaffold Detection
        // If pom.xml is missing, assume it's a new project, even if other junk files exist.
        File pom = new File(workspaceDir, "pom.xml");
        if (!pom.exists()) {
            log.info("No 'pom.xml' found in {}. Treating as NEW/EMPTY project (forcing Scaffold mode).", repoName);
            return false;
        }

        List<File> javaFiles = findJavaFiles(workspaceDir);

        if (javaFiles.isEmpty()) {
            log.info("No Java files found in {}. Treating as NEW/EMPTY project.", repoName);
            return false;
        }

        log.info("Found {} Java files. Starting Ingestion...", javaFiles.size());

        List<VectorWithUnsignedIndices> vectorsToUpsert = new ArrayList<>();

        for (File file : javaFiles) {
            try {
                processFile(file, workspaceDir, repoName, vectorsToUpsert);

                // ✅ FIX 2: Throttling to prevent 429 Too Many Requests
                try { Thread.sleep(4000); } catch (InterruptedException ignored) {}

            } catch (Exception e) {
                log.warn("Skipping file {} due to error: {}", file.getName(), e.getMessage());
            }
        }

        if (!vectorsToUpsert.isEmpty()) {
            upsertBatch(vectorsToUpsert);
            log.info("Synced {} vectors to Pinecone for {}", vectorsToUpsert.size(), repoName);
        }

        return true;
    }

    private void processFile(File file, File rootDir, String repoName, List<VectorWithUnsignedIndices> accumulator) throws IOException {
        String content = Files.readString(file.toPath());

        // Skip noise or huge files
        if (content.length() < 50 || content.length() > 25000) return;

        // 1. Get Embedding
        List<Double> embedding = geminiClient.createEmbedding(content);
        List<Float> floatVector = embedding.stream().map(Double::floatValue).toList();

        // 2. Build Metadata
        String relativePath = getRelativePath(rootDir, file);

        Struct metadata = Struct.newBuilder()
                .putFields("repo_name", Value.newBuilder().setStringValue(repoName).build())
                .putFields("file_path", Value.newBuilder().setStringValue(relativePath).build())
                .putFields("content", Value.newBuilder().setStringValue(content).build())
                .build();

        // 3. Create Vector ID
        String vectorId = repoName + ":" + relativePath;

        VectorWithUnsignedIndices vector = new VectorWithUnsignedIndices(
                vectorId,
                floatVector,
                metadata,
                null
        );

        accumulator.add(vector);
    }

    private void upsertBatch(List<VectorWithUnsignedIndices> vectors) {
        pineconeClient.getIndexConnection(indexName).upsert(
                vectors,
                ""
        );
    }

    private List<File> findJavaFiles(File root) {
        try (Stream<Path> walk = Files.walk(root.toPath())) {
            return walk.filter(p -> !Files.isDirectory(p))
                    .map(Path::toFile)
                    .filter(f -> f.getName().endsWith(".java"))
                    .filter(f -> !f.getAbsolutePath().contains("src" + File.separator + "test"))
                    .toList();
        } catch (IOException e) {
            log.error("Failed to walk directory: {}", root, e);
            return new ArrayList<>();
        }
    }

    private String getRelativePath(File root, File file) {
        return root.toURI().relativize(file.toURI()).getPath();
    }
}