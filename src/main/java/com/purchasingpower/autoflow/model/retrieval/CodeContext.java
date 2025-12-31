package com.purchasingpower.autoflow.model.retrieval;

/**
 * Structured code context from Pinecone semantic search.
 *
 * Represents a single code chunk (class, method, or field) retrieved
 * from Pinecone vector database with similarity score and metadata.
 *
 * @param id Unique identifier for the code chunk
 * @param score Similarity score from Pinecone (0.0 to 1.0)
 * @param chunkType Type of chunk (CLASS, METHOD, FIELD, etc.)
 * @param className Fully qualified class name
 * @param methodName Method name (if applicable)
 * @param filePath Source file path
 * @param content Code content or preview
 *
 * @see com.purchasingpower.autoflow.client.PineconeRetriever
 */
public record CodeContext(
        String id,
        float score,
        String chunkType,
        String className,
        String methodName,
        String filePath,
        String content
) {
}
