package com.purchasingpower.autoflow.workflow.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of analyzing logs (if user provided logs)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogAnalysis {

    /**
     * Type of error found
     * Examples: "NullPointerException", "TimeoutException", "SQLException"
     */
    private String errorType;

    /**
     * Where the error occurred
     * Format: "ClassName.java:lineNumber"
     * Example: "PaymentService.java:42"
     */
    private String location;

    /**
     * Full stack trace (if available)
     */
    private String stackTrace;

    /**
     * Agent's hypothesis about root cause
     */
    private String rootCauseHypothesis;

    /**
     * Methods/classes affected by this error
     */
    @Builder.Default
    private List<String> affectedMethods = new ArrayList<>();

    /**
     * Questions agent needs answered
     */
    @Builder.Default
    private List<String> questions = new ArrayList<>();

    /**
     * Confidence in analysis (0.0 - 1.0)
     */
    private double confidence;
}