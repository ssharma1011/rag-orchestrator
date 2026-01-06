package com.purchasingpower.autoflow.knowledge;

import com.purchasingpower.autoflow.model.java.JavaClass;
import com.purchasingpower.autoflow.model.java.JavaMethod;

/**
 * Generates enriched text descriptions for Java code elements.
 *
 * These descriptions are optimized for embedding generation, capturing semantic
 * meaning rather than syntax. Used for vector search.
 *
 * @since 2.0.0
 */
public interface DescriptionGenerator {

    /**
     * Generate enriched description for a Java class.
     *
     * Output format:
     * - Class name and inferred purpose
     * - Package and domain context
     * - Annotations
     * - Extends/implements relationships
     * - Key methods (names only)
     * - Dependencies
     *
     * @param javaClass the class to describe
     * @return enriched text description suitable for embedding
     */
    String generateClassDescription(JavaClass javaClass);

    /**
     * Generate enriched description for a Java method.
     *
     * Output format:
     * - Method signature and inferred purpose
     * - Annotations
     * - Parameters and return type
     * - Method calls (what it depends on)
     *
     * @param method the method to describe
     * @param parentClass the class containing this method
     * @return enriched text description suitable for embedding
     */
    String generateMethodDescription(JavaMethod method, JavaClass parentClass);
}
