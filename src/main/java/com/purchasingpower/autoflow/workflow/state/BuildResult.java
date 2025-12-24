package com.purchasingpower.autoflow.workflow.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of Maven build
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildResult implements Serializable {

    private boolean success;

    @Builder.Default
    private List<String> compilationErrors = new ArrayList<>();

    private String buildLogs;

    private long durationMs;

    /**
     * Formatted error message for display
     */
    public String getErrors() {
        if (compilationErrors.isEmpty()) {
            return "";
        }
        return String.join("\n", compilationErrors);
    }
}