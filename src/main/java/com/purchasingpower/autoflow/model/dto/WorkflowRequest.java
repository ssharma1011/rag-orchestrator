package com.purchasingpower.autoflow.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for starting a new workflow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRequest {
    private String requirement;
    private String repoUrl;
    private String baseBranch;
    private String jiraUrl;
    private String logsPasted;
    private String userId;
}
