package com.purchasingpower.autoflow.model.agent;

/**
 * Method-level match from code search.
 *
 * Tracks individual methods within classes that are
 * similar to the requirement, allowing for precise targeting
 * of code changes.
 *
 * @param methodName Name of the matched method
 * @param score Similarity score (0.0 to 1.0)
 * @param content Method source code or description
 * @param chunkType Type of chunk (METHOD, FIELD, CLASS, etc.)
 *
 * @see com.purchasingpower.autoflow.workflow.agents.ScopeDiscoveryAgent
 */
public record MethodMatch(String methodName, float score, String content, String chunkType) {
}
