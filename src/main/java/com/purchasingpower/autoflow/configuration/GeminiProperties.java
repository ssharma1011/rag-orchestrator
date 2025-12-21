package com.purchasingpower.autoflow.configuration;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GeminiProperties {

    @NotBlank
    private String apiKey;

    @NotBlank
    private String chatModel = "gemini-1.5-pro";

    @NotBlank
    private String embeddingModel = "text-embedding-004";

    @NotBlank
    private String baseUrl = "https://generativelanguage.googleapis.com";

    @NotBlank
    private String apiVersion = "v1beta";
}