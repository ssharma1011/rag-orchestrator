package com.purchasingpower.autoflow.client;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.purchasingpower.autoflow.configuration.AppProperties;
import io.pinecone.clients.Pinecone;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Retrieves relevant code from Pinecone vector database.
 *
 * HOW IT WORKS (Vector Search, not SQL!):
 * ════════════════════════════════════════════════════════════════════════
 *
 * 1. Your query: "How to create embeddings?"
 *    ↓
 * 2. Query is converted to embedding vector (768 numbers) by GeminiClient
 *    ↓
 * 3. Pinecone calculates COSINE SIMILARITY between query vector and ALL stored vectors
 *    ↓
 * 4. Returns the TOP-K most similar vectors (closest in meaning)
 *    ↓
 * 5. We extract the code content from the returned vectors' metadata
 *
 * KEY INSIGHT:
 * - "How to create embeddings?" and "batchCreateEmbeddings()" produce SIMILAR vectors
 * - Because Gemini learned they have similar MEANING during training
 * - This is called SEMANTIC SEARCH (meaning-based, not keyword-based)
 *
 * ════════════════════════════════════════════════════════════════════════
 */
@Slf4j
@Component
public class PineconeRetriever {

    private final Pinecone client;
    private final String indexName;

    public PineconeRetriever(AppProperties props) {
        this.client = new Pinecone.Builder(props.getPinecone().getApiKey()).build();
        this.indexName = props.getPinecone().getIndexName();
    }

    /**
     * Finds code chunks most similar to the query vector.
     *
     * @param vector The query embedding (768 dimensions from Gemini)
     * @param repoName Repository to search within (filters results)
     * @return Formatted string containing relevant code snippets
     */
    public String findRelevantCode(List<Double> vector, String repoName) {
        try {
            // Convert Double to Float (Pinecone uses float32)
            List<Float> floats = vector.stream()
                    .map(Double::floatValue)
                    .toList();

            // ✅ FIX: Build filter to search only within this repository
            Struct filter = buildRepoFilter(repoName);

            log.debug("Querying Pinecone index '{}' for repo '{}' with filter", indexName, repoName);

            QueryResponseWithUnsignedIndices response = client.getIndexConnection(indexName)
                    .query(
                            20,      // topK - return top 20 most similar vectors
                            floats,  // vector - the query embedding
                            null,    // sparseIndices (not using hybrid search)
                            null,    // sparseValues (not using hybrid search)
                            null,    // id (not querying by specific vector ID)
                            "",      // namespace (empty = default namespace)
                            filter,  // ✅ Filter by repo_name
                            false,   // includeValues (we don't need the vectors back)
                            true     // includeMetadata (we need the code content!)
                    );

            if (response.getMatchesList() == null || response.getMatchesList().isEmpty()) {
                log.warn("No matches found in Pinecone for repo '{}'", repoName);
                return "NO CONTEXT FOUND";
            }

            log.info("Found {} matches in Pinecone", response.getMatchesList().size());

            // Format results for LLM consumption
            return formatResults(response.getMatchesList());

        } catch (Exception e) {
            log.error("Pinecone Query Error: {}", e.getMessage(), e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Builds a Pinecone filter to search only within a specific repository.
     *
     * Equivalent to SQL: WHERE repo_name = 'autoflow'
     *
     * Pinecone filter format:
     * {
     *   "repo_name": { "$eq": "autoflow" }
     * }
     */
    private Struct buildRepoFilter(String repoName) {
        if (repoName == null || repoName.isEmpty()) {
            return null; // No filter - search all
        }

        return Struct.newBuilder()
                .putFields("repo_name", Value.newBuilder()
                        .setStructValue(Struct.newBuilder()
                                .putFields("$eq", Value.newBuilder()
                                        .setStringValue(repoName)
                                        .build())
                                .build())
                        .build())
                .build();
    }

    /**
     * Formats Pinecone results into a readable string for the LLM.
     *
     * Includes:
     * - Chunk type (CLASS, METHOD, FIELD)
     * - Similarity score (how relevant)
     * - File path
     * - Actual code content
     */
    private String formatResults(List<ScoredVectorWithUnsignedIndices> matches) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < matches.size(); i++) {
            ScoredVectorWithUnsignedIndices match = matches.get(i);

            String chunkType = "UNKNOWN";
            String className = "";
            String methodName = "";
            String filePath = "";
            String content = "";
            float score = match.getScore();

            // Extract metadata
            if (match.getMetadata() != null) {
                var fields = match.getMetadata().getFieldsMap();

                if (fields.containsKey("chunk_type")) {
                    chunkType = fields.get("chunk_type").getStringValue();
                }
                if (fields.containsKey("class_name")) {
                    className = fields.get("class_name").getStringValue();
                }
                if (fields.containsKey("method_name")) {
                    methodName = fields.get("method_name").getStringValue();
                }
                if (fields.containsKey("file_path")) {
                    filePath = fields.get("file_path").getStringValue();
                }
                if (fields.containsKey("content")) {
                    content = fields.get("content").getStringValue();
                }
            }

            // Format output
            sb.append(String.format("--- MATCH %d [%s] (score: %.3f) ---\n", i + 1, chunkType, score));

            if (!className.isEmpty()) {
                sb.append("Class: ").append(className);
                if (!methodName.isEmpty() && !methodName.equals(className)) {
                    sb.append(" | Method: ").append(methodName);
                }
                sb.append("\n");
            }

            if (!filePath.isEmpty()) {
                sb.append("File: ").append(filePath).append("\n");
            }

            sb.append("\n").append(content).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * Alternative method: Returns structured results instead of formatted string.
     * Useful if you want to process results programmatically.
     */
    public List<CodeContext> findRelevantCodeStructured(List<Double> vector, String repoName) {
        try {
            List<Float> floats = vector.stream().map(Double::floatValue).toList();
            Struct filter = buildRepoFilter(repoName);

            QueryResponseWithUnsignedIndices response = client.getIndexConnection(indexName)
                    .query(20, floats, null, null, null, "", filter, false, true);

            if (response.getMatchesList() == null) {
                return List.of();
            }

            return response.getMatchesList().stream()
                    .map(this::toCodeContext)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Pinecone Query Error: {}", e.getMessage());
            return List.of();
        }
    }

    private CodeContext toCodeContext(ScoredVectorWithUnsignedIndices match) {
        var fields = match.getMetadata() != null ? match.getMetadata().getFieldsMap() : null;

        return new CodeContext(
                match.getId(),
                match.getScore(),
                fields != null && fields.containsKey("chunk_type") ? fields.get("chunk_type").getStringValue() : "UNKNOWN",
                fields != null && fields.containsKey("class_name") ? fields.get("class_name").getStringValue() : "",
                fields != null && fields.containsKey("method_name") ? fields.get("method_name").getStringValue() : "",
                fields != null && fields.containsKey("file_path") ? fields.get("file_path").getStringValue() : "",
                fields != null && fields.containsKey("content") ? fields.get("content").getStringValue() : ""
        );
    }

    /**
     * Simple record to hold structured code context.
     */
    public record CodeContext(
            String id,
            float score,
            String chunkType,
            String className,
            String methodName,
            String filePath,
            String content
    ) {}
}