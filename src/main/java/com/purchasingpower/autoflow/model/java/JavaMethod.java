package com.purchasingpower.autoflow.model.java;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * Represents a parsed Java method.
 *
 * @since 2.0.0
 */
@Value
@Builder
public class JavaMethod {
    String id;
    String name;
    String signature;
    List<String> annotations;
    List<JavaParameter> parameters;
    String returnType;
    int startLine;
    int endLine;
    List<String> methodCalls; // Names of methods called
    String description; // Enriched text for embedding
    List<Double> embedding; // Vector embedding (1024 dims for mxbai-embed-large)
}
