package com.purchasingpower.autoflow.model.prompt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Prompt template loaded from YAML configuration.
 *
 * Represents a structured prompt with metadata (name, version, model)
 * and content (system and user prompts) for LLM interactions.
 *
 * YAML structure:
 * <pre>
 * name: scope-discovery
 * version: 1.0
 * model: gemini-1.5-flash
 * temperature: 0.3
 * systemPrompt: |
 *   You are an expert...
 * userPrompt: |
 *   Analyze this requirement...
 * </pre>
 *
 * @see com.purchasingpower.autoflow.service.PromptLibraryService
 */
@JsonIgnoreProperties(ignoreUnknown = true)  // Allow extra fields like "examples" for documentation
public class PromptTemplate {
    private String name;
    private String version;
    private String model;
    private double temperature;
    private String systemPrompt;
    private String userPrompt;

    // Getters/setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public void setUserPrompt(String userPrompt) {
        this.userPrompt = userPrompt;
    }
}
