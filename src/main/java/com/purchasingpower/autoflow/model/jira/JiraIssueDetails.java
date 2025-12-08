package com.purchasingpower.autoflow.model.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;



@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssueDetails {
    private String key;
    private JiraFields fields;
}
