package com.purchasingpower.autoflow.agent;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * Default implementation of ToolResult.
 *
 * @since 2.0.0
 */
@Getter
@AllArgsConstructor
public class DefaultToolResult implements ToolResult {

    private final boolean success;
    private final Object data;
    private final String message;
    private final Map<String, Object> metadata;
    private final String[] suggestedNextTools;

    @Override
    public boolean isSuccess() {
        return success;
    }
}
