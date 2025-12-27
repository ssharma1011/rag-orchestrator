package com.purchasingpower.autoflow.model.ast;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single embeddable unit of code for vector storage.
 * Implements hierarchical chunking: parent chunks (classes) contain child chunks (methods).
 *
 * Example Structure:
 * Parent: payment-service:PaymentService.java (class metadata)
 *   ├─ Child: payment-service:PaymentService.processPayment() (method body)
 *   ├─ Child: payment-service:PaymentService.validatePayment() (method body)
 *   └─ Child: payment-service:PaymentService.refundPayment() (method body)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeChunk {

    /**
     * Unique identifier for this chunk in vector storage.
     * Format: {repoName}:{filePath} for classes
     *         {repoName}:{className}.{methodName} for methods
     *
     * Examples:
     * - "payment-service:src/main/java/com/example/PaymentService.java" (parent)
     * - "payment-service:PaymentService.processPayment" (child)
     */
    private String id;

    /**
     * Type of code element this chunk represents
     */
    private ChunkType type;

    /**
     * Repository name (for filtering in Pinecone queries)
     */
    private String repoName;

    /**
     * The actual text content to be embedded.
     *
     * For CLASS chunks: Summary text (annotations + interfaces + field names)
     * For METHOD chunks: Full method body source code
     */
    private String content;

    // ========================================================================
    // HIERARCHICAL RELATIONSHIP
    // ========================================================================

    /**
     * ID of the parent chunk (null if this IS the parent)
     * For method chunks, this points to the class chunk ID
     */
    private String parentChunkId;

    /**
     * IDs of child chunks (empty if this IS a child)
     * For class chunks, this lists all method chunk IDs
     */
    @Builder.Default
    private List<String> childChunkIds = new ArrayList<>();

    // ========================================================================
    // STRUCTURED METADATA
    // ========================================================================

    /**
     * Detailed class metadata (populated if type = CLASS/INTERFACE/ENUM)
     * Null for METHOD chunks
     */
    private ClassMetadata classMetadata;

    /**
     * Detailed method metadata (populated if type = METHOD/CONSTRUCTOR)
     * Null for CLASS chunks
     */
    private MethodMetadata methodMetadata;

    // ========================================================================
    // PINECONE STORAGE FORMAT
    // ========================================================================

    /**
     * Flattened metadata for Pinecone storage.
     * Pinecone requires String key-value pairs (Struct format).
     *
     * We flatten ClassMetadata/MethodMetadata into simple strings:
     * {
     *   "chunk_type": "METHOD",
     *   "repo_name": "payment-service",
     *   "class_name": "PaymentService",
     *   "method_name": "processPayment",
     *   "annotations": "@Transactional,@Async",
     *   "return_type": "PaymentResponse",
     *   "parent_chunk_id": "payment-service:PaymentService.java",
     *   "content": "<full method body>"
     * }
     */
    @Builder.Default
    private Map<String, String> flatMetadata = new HashMap<>();

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Converts structured metadata into flat String map for Pinecone.
     * Called before upserting to vector database.
     */
    public Map<String, String> toFlatMetadata() {
        Map<String, String> flat = new HashMap<>();

        // Common fields
        flat.put("chunk_type", type.toString());
        flat.put("repo_name", repoName);

        // CRITICAL: Pinecone metadata limit is 40KB per vector
        // Store only a preview of content (first 500 chars) to avoid exceeding limit
        // Full content is already embedded in the vector itself
        if (content != null) {
            String contentPreview = content.length() > 500
                ? content.substring(0, 500) + "..."
                : content;
            flat.put("content_preview", contentPreview);
        }

        if (parentChunkId != null) {
            flat.put("parent_chunk_id", parentChunkId);
        }

        // Class-specific fields
        if (classMetadata != null) {
            flat.put("class_name", classMetadata.getClassName());
            flat.put("fully_qualified_name", classMetadata.getFullyQualifiedName());
            flat.put("package_name", classMetadata.getPackageName());
            flat.put("annotations", String.join(",", classMetadata.getAnnotations()));
            flat.put("interfaces", String.join(",", classMetadata.getImplementedInterfaces()));
            flat.put("libraries", String.join(",", classMetadata.getUsedLibraries()));
            flat.put("file_path", classMetadata.getSourceFilePath());

            if (classMetadata.getClassSummary() != null) {
                flat.put("class_summary", classMetadata.getClassSummary());
            }
        }

        // Method-specific fields
        if (methodMetadata != null) {
            flat.put("method_name", methodMetadata.getMethodName());
            flat.put("fully_qualified_name", methodMetadata.getFullyQualifiedName());
            flat.put("owning_class", methodMetadata.getOwningClass());
            flat.put("return_type", methodMetadata.getReturnType());
            flat.put("annotations", String.join(",", methodMetadata.getAnnotations()));
            flat.put("parameters", String.join(",", methodMetadata.getParameters()));
            flat.put("called_methods", String.join(",", methodMetadata.getCalledMethods()));
            flat.put("line_count", String.valueOf(methodMetadata.getLineCount()));

            if (methodMetadata.getMethodSummary() != null) {
                flat.put("method_summary", methodMetadata.getMethodSummary());
            }
        }

        this.flatMetadata = flat;
        return flat;
    }

    /**
     * Checks if this chunk is a parent (contains children)
     */
    public boolean isParent() {
        return childChunkIds != null && !childChunkIds.isEmpty();
    }

    /**
     * Checks if this chunk is a child (has a parent)
     */
    public boolean isChild() {
        return parentChunkId != null;
    }
}