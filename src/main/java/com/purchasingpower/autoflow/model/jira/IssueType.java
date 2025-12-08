package com.purchasingpower.autoflow.model.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IssueType {
    private String name; // e.g., "Story", "Bug"
    private String id;
}
