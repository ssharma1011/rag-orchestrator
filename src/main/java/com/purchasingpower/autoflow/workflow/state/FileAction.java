package com.purchasingpower.autoflow.workflow.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Describes an action to take on a file.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileAction implements Serializable {

    /**
     * File path relative to repo root
     * Example: "src/main/java/com/example/PaymentService.java"
     */
    private String filePath;

    /**
     * What to do with this file
     */
    private ActionType actionType;

    /**
     * Why this file needs this action
     */
    private String reason;

    /**
     * Fully qualified class name (for Java files)
     */
    private String className;

    public enum ActionType {
        MODIFY,   // Change existing file
        CREATE,   // Create new file
        DELETE    // Delete file (rare)
    }
}