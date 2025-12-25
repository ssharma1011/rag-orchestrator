package com.purchasingpower.autoflow.workflow.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of Maven build
 * CRITICAL FIX: Ensure compilationErrors is never null for Jackson serialization
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BuildResult implements Serializable {

    private boolean success;

    /**
     * CRITICAL: Initialize to empty list to prevent NPE during serialization
     * Jackson doesn't respect @Builder.Default during deserialization
     */
    private List<String> compilationErrors;

    private String buildLogs;

    private long durationMs;

    /**
     * Custom getter that GUARANTEES non-null list
     */
    public List<String> getCompilationErrors() {
        if (compilationErrors == null) {
            compilationErrors = new ArrayList<>();
        }
        return compilationErrors;
    }

    /**
     * Formatted error message for display
     */
    public String getErrors() {
        if (getCompilationErrors().isEmpty()) {
            return "";
        }
        return String.join("\n", compilationErrors);
    }

    /**
     * Builder customization to ensure default values
     */
    public static class BuildResultBuilder {
        public BuildResult build() {
            if (compilationErrors == null) {
                compilationErrors = new ArrayList<>();
            }
            return new BuildResult(success, compilationErrors, buildLogs, durationMs);
        }
    }
}