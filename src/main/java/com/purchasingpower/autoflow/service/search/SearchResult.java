package com.purchasingpower.autoflow.service.search;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Immutable web search result.
 *
 * <p>Contains answer text and source URLs for verification.
 *
 * @since 1.0.0
 */
@Value
@Builder
public class SearchResult {

    /**
     * Synthesized answer from search results.
     *
     * <p>This is the text that will be fed to the LLM
     * to help it generate correct code.
     */
    String answer;

    /**
     * Source URLs where information was found.
     *
     * <p>For transparency and verification.
     */
    @Builder.Default
    List<String> sources = List.of();

    /**
     * Confidence score (0-100).
     *
     * <p>Higher means more authoritative sources
     * (e.g., official documentation vs Stack Overflow).
     */
    int confidence;

    /**
     * Time taken to search in milliseconds.
     */
    long searchTimeMs;

    /**
     * Creates an empty result when search fails or returns nothing.
     */
    public static SearchResult empty() {
        return SearchResult.builder()
                .answer("No results found")
                .confidence(0)
                .searchTimeMs(0)
                .build();
    }

    /**
     * Formats result for LLM consumption.
     *
     * <p>Includes answer and sources in a clear format.
     */
    public String formatForLLM() {
        if (confidence == 0) {
            return "No web search results available.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Web Search Results:\n\n");
        sb.append(answer).append("\n\n");

        if (!sources.isEmpty()) {
            sb.append("Sources:\n");
            sources.forEach(source -> sb.append("- ").append(source).append("\n"));
        }

        return sb.toString();
    }
}
