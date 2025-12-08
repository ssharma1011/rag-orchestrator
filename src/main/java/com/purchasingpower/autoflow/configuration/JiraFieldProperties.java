package com.purchasingpower.autoflow.configuration;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JiraFieldProperties {

    @NotBlank
    private String repoUrlFieldId;

    @NotBlank
    private String repoNameFieldId;

    @NotBlank
    private String baseBranchFieldId;

    @NotBlank
    private String acceptanceCriteriaFieldId = "customfield_10143";
}