package com.purchasingpower.autoflow.configuration;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PineconeProperties {

    @NotBlank
    private String apiKey;

    @NotBlank
    private String indexName;
}