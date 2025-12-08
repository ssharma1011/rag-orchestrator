package com.purchasingpower.autoflow.model.jira;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraFields {


    private String summary;
    private Object description;
    private Status status;
    private IssueType issuetype;

    private Map<String, Object> customFields = new HashMap<>();
    @JsonAnySetter
    public void handleDynamicField(String key, Object value) {
        this.customFields.put(key, value);
    }

    /**
     * ✅ FIX: Call this method in your code instead of getDescription()
     */
    public String getDescriptionText() {
        if (description == null) return "";
        if (description instanceof String) return (String) description;
        return description.toString();
    }
}