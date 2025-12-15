package com.purchasingpower.autoflow.model.ast;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Metadata extracted from a Java method during AST parsing.
 * Used to create "child" chunks in hierarchical vector storage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodMetadata {

    /**
     * Simple method name: generateCodePlan
     */
    private String methodName;

    /**
     * Fully qualified method signature: GeminiClient.generateCodePlan(String, String)
     */
    private String fullyQualifiedName;

    /**
     * Fully qualified name of the class that owns this method
     * Example: com.purchasingpower.autoflow.client.GeminiClient
     */
    private String owningClass;

    /**
     * Parameter types and names: [String requirements, String context]
     */
    @Builder.Default
    private List<String> parameters = new ArrayList<>();

    /**
     * Return type: CodeGenerationResponse, void, String, etc.
     */
    private String returnType;

    /**
     * Annotations on the method: [@Override, @Transactional, @Async]
     */
    @Builder.Default
    private List<String> annotations = new ArrayList<>();

    /**
     * Complete method body source code (for embedding and code generation)
     */
    private String methodBody;

    /**
     * Methods called within this method body
     * Example: [buildMaintainerPrompt, callChatApi, objectMapper.readValue]
     * Used for dependency graph construction
     */
    @Builder.Default
    private List<String> calledMethods = new ArrayList<>();

    /**
     * Exceptions declared in throws clause: [IOException, RuntimeException]
     */
    @Builder.Default
    private List<String> thrownExceptions = new ArrayList<>();

    /**
     * Visibility modifier
     */
    private boolean isPublic;

    /**
     * Whether the method is static
     */
    private boolean isStatic;

    /**
     * Whether the method is private
     */
    private boolean isPrivate;

    /**
     * Whether the method is protected
     */
    private boolean isProtected;

    /**
     * Number of lines in the method body
     */
    private int lineCount;

    /**
     * Cyclomatic complexity (number of decision points)
     * Used to identify complex methods that might need special handling
     */
    private int cyclomaticComplexity;

    /**
     * Brief description of what the method does (from JavaDoc or inferred)
     */
    private String methodSummary;
}
