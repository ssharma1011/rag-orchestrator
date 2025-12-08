package com.purchasingpower.autoflow.configuration;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BitbucketProperties {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String workspace;

    @NotBlank
    private String username;

    @NotBlank
    private String appPassword;

    @NotBlank
    private String apiUsername;

}
