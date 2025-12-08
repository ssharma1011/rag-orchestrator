package com.purchasingpower.autoflow.model.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodeGenerationResponse {

    @JsonProperty("branch_name")
    private String branchName;

    @JsonProperty("edits")
    private List<FileEdit> edits;

    @JsonProperty("tests_added")
    private List<TestFile> testsAdded;

    private String explanation;
}