package com.purchasingpower.autoflow.model.java;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * Represents a parsed Java field.
 *
 * @since 2.0.0
 */
@Value
@Builder
public class JavaField {
    String id;
    String name;
    String type;
    List<String> annotations;
    int lineNumber;
}
