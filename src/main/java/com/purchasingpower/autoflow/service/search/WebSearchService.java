package com.purchasingpower.autoflow.service.search;

/**
 * Searches the web for current library documentation and API information.
 *
 * <p>Used as fallback when AI generates code with outdated library APIs.
 * Provides real-time access to latest documentation.
 *
 * <p><b>Thread Safety:</b> Implementations must be thread-safe.
 *
 * @since 1.0.0
 */
public interface WebSearchService {

    /**
     * Searches for information about a specific error or API.
     *
     * <p>Optimized for finding:
     * - Latest library API signatures
     * - Solutions to compilation errors
     * - Migration guides for deprecated APIs
     *
     * @param query Search query (e.g., "Neo4j executeWrite Java API 2024")
     * @return Search result with answer and sources
     * @throws IllegalArgumentException if query is null or empty
     */
    SearchResult search(String query);

    /**
     * Searches specifically for library API documentation.
     *
     * <p>Targets official documentation sites for accurate information.
     *
     * @param libraryName Name of library (e.g., "Neo4j", "Spring Boot")
     * @param apiName     API/method name (e.g., "executeWrite", "Transaction")
     * @return Search result with API documentation
     */
    SearchResult searchLibraryAPI(String libraryName, String apiName);

    /**
     * Checks if web search is enabled.
     *
     * <p>Allows graceful degradation if API keys are missing
     * or service is disabled in configuration.
     *
     * @return true if web search is available
     */
    boolean isEnabled();
}
