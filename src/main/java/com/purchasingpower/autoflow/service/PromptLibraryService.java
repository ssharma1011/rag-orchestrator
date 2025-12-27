package com.purchasingpower.autoflow.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt Library Service
 *
 * Loads prompts from YAML files and renders them with variables.
 *
 * Usage:
 * String prompt = promptLibrary.render("scope-discovery", Map.of(
 *     "requirement", "Fix payment bug",
 *     "domain", "payment",
 *     "candidates", candidatesList
 * ));
 */
@Slf4j
@Service
public class PromptLibraryService {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();
    private final Map<String, PromptTemplate> templates = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadPrompts() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:prompts/*.yaml");

            for (Resource resource : resources) {
                PromptTemplate template = yamlMapper.readValue(
                        resource.getInputStream(),
                        PromptTemplate.class
                );

                templates.put(template.getName(), template);
                log.info("Loaded prompt template: {} (version: {})",
                        template.getName(), template.getVersion());
            }

            log.info("Loaded {} prompt templates", templates.size());

        } catch (Exception e) {
            log.error("Failed to load prompt templates", e);
            throw new RuntimeException("Prompt library initialization failed", e);
        }
    }

    /**
     * Render a prompt with variables
     */
    public String render(String templateName, Map<String, Object> variables) {
        PromptTemplate template = templates.get(templateName);

        if (template == null) {
            throw new IllegalArgumentException("Prompt template not found: " + templateName);
        }

        // Combine system + user prompts
        String fullPrompt = template.getSystemPrompt() + "\n\n" + template.getUserPrompt();

        // Render with Mustache
        Mustache mustache = mustacheFactory.compile(
                new StringReader(fullPrompt),
                templateName
        );

        StringWriter writer = new StringWriter();
        mustache.execute(writer, variables);

        return writer.toString();
    }

    /**
     * Get template metadata (for logging, debugging)
     */
    public PromptTemplate getTemplate(String name) {
        return templates.get(name);
    }

    /**
     * YAML structure
     */
    @JsonIgnoreProperties(ignoreUnknown = true)  // Allow extra fields like "examples" for documentation
    public static class PromptTemplate {
        private String name;
        private String version;
        private String model;
        private double temperature;
        private String systemPrompt;
        private String userPrompt;

        // Getters/setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public String getSystemPrompt() { return systemPrompt; }
        public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

        public String getUserPrompt() { return userPrompt; }
        public void setUserPrompt(String userPrompt) { this.userPrompt = userPrompt; }
    }
}