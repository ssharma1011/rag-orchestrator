package com.purchasingpower.autoflow.model.java;

import lombok.Builder;
import lombok.Value;

/**
 * Represents a Java method parameter.
 *
 * @since 2.0.0
 */
@Value
@Builder
public class JavaParameter {
    String name;
    String type;
}
