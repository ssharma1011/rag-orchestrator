package com.purchasingpower.autoflow.configuration;

import com.purchasingpower.autoflow.model.library.LibraryDefinition;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for library detection rules.
 * Binds to autoflow.library-detection in library-rules.yml
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "autoflow.library-detection")
public class LibraryDetectionProperties {
    
    private List<LibraryDefinition> libraries = new ArrayList<>();
}
