package com.purchasingpower.autoflow.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for cost analysis API.
 *
 * Provides total cost of LLM usage within a specified time period,
 * useful for tracking expenses and budgeting.
 *
 * @see com.purchasingpower.autoflow.controller.LLMMetricsController
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostAnalysisResponse {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private double totalCost;
}
