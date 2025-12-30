package com.purchasingpower.autoflow.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Configuration class that enables all @ConfigurationProperties classes.
 *
 * <p>This configuration ensures that all custom application configuration classes
 * are properly initialized and bound from application.yml. While individual classes
 * use @ConfigurationProperties annotations, this central enabler ensures they are
 * all registered with Spring's property binding mechanism.
 *
 * <p>Enabled configuration classes:
 * <ul>
 *   <li>{@link GeminiConfig} - Google Gemini API configuration
 *   <li>{@link ScopeDiscoveryConfig} - Scope discovery agent settings
 *   <li>{@link GlobalRetryConfig} - Global retry and backoff settings
 *   <li>{@link AgentConfig} - Umbrella configuration for all agents
 * </ul>
 *
 * <p><b>Note:</b> ScopeApprovalAgent uses LLM-based natural language understanding
 * instead of keyword configuration, so no config class is needed.
 *
 * <p><b>Why This Class Is Needed:</b>
 * While @Component and @ConfigurationProperties on individual classes makes them
 * discoverable via component scanning, explicitly enabling them here:
 * <ul>
 *   <li>Makes dependencies explicit and clear
 *   <li>Ensures proper initialization order
 *   <li>Centralizes configuration class management
 *   <li>Improves IDE support and documentation
 * </ul>
 *
 * <p><b>Thread Safety:</b> This is a Spring-managed singleton configuration class
 * that is thread-safe.
 *
 * @author AutoFlow Pipeline
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties({
    GeminiConfig.class,
    ScopeDiscoveryConfig.class,
    GlobalRetryConfig.class,
    AgentConfig.class
})
public class ConfigurationPropertiesEnablerConfig {
    // Spring automatically instantiates and manages the configuration classes
    // listed in @EnableConfigurationProperties. No additional beans need to be
    // created in this class.
}
