package com.purchasingpower.autoflow.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @NotBlank(message = "Workspace directory path is required")
    private String workspaceDir;

    @Valid
    @NotNull
    @NestedConfigurationProperty
    private JiraProperties jira = new JiraProperties();

    @Valid
    @NotNull
    @NestedConfigurationProperty
    private BitbucketProperties bitbucket = new BitbucketProperties();

    @Valid
    @NotNull
    @NestedConfigurationProperty
    private GeminiProperties gemini = new GeminiProperties();

    @Valid
    @NotNull
    @NestedConfigurationProperty
    private PineconeProperties pinecone = new PineconeProperties();
}