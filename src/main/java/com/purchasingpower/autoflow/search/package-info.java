/**
 * Search engine: structural, semantic, and temporal queries.
 *
 * <p>This package implements the three-mode search strategy:
 * <ul>
 *   <li>Structural - Graph queries for relationships (CALLS, DEPENDS_ON, etc.)</li>
 *   <li>Semantic - Vector similarity for natural language queries</li>
 *   <li>Temporal - Git history for change tracking</li>
 * </ul>
 *
 * <p>Key classes:
 * <ul>
 *   <li>{@code SearchOrchestrator} - Coordinates multiple search strategies</li>
 *   <li>{@code StructuralSearcher} - Cypher-based graph queries</li>
 *   <li>{@code SemanticSearcher} - Vector similarity search</li>
 *   <li>{@code TemporalSearcher} - Git history queries</li>
 * </ul>
 *
 * @since 2.0.0
 */
package com.purchasingpower.autoflow.search;
