package com.purchasingpower.autoflow.configuration;



import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
public class JiraProperties {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String username;

    @NotBlank
    private String token;

    @Valid
    @NotNull
    @NestedConfigurationProperty
    private JiraFieldProperties fields = new JiraFieldProperties();
}
