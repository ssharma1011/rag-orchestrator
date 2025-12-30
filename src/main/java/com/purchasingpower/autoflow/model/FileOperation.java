package com.purchasingpower.autoflow.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Enumeration of file operations that can be performed during code generation.
 *
 * <p>Replaces string literals ("create", "modify", "delete") with type-safe enum.
 *
 * <p>Each operation implements a strategy pattern for file manipulation.
 *
 * @see BuildValidatorAgent
 */
public enum FileOperation {

    /**
     * Create a new file with content.
     * Creates parent directories if they don't exist.
     */
    CREATE {
        @Override
        public void execute(File file, String content) throws IOException {
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), content);
        }
    },

    /**
     * Modify an existing file by replacing its content.
     * Identical to CREATE but semantically different (indicates file already exists).
     */
    MODIFY {
        @Override
        public void execute(File file, String content) throws IOException {
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), content);
        }
    },

    /**
     * Delete an existing file.
     * Content parameter is ignored for this operation.
     */
    DELETE {
        @Override
        public void execute(File file, String content) throws IOException {
            if (file.exists()) {
                file.delete();
            }
        }
    };

    /**
     * Execute the file operation.
     *
     * @param file    the file to operate on
     * @param content the content to write (ignored for DELETE)
     * @throws IOException if file operation fails
     */
    public abstract void execute(File file, String content) throws IOException;

    /**
     * Parse operation from string (case-insensitive).
     * Safely converts legacy string values to enum.
     *
     * @param op the operation string ("create", "modify", "delete")
     * @return FileOperation enum
     * @throws IllegalArgumentException if operation is invalid
     */
    public static FileOperation fromString(String op) {
        if (op == null) {
            throw new IllegalArgumentException("File operation cannot be null");
        }
        return FileOperation.valueOf(op.toUpperCase());
    }

    /**
     * Check if this operation creates or modifies file content.
     *
     * @return true for CREATE and MODIFY, false for DELETE
     */
    public boolean isWrite() {
        return this == CREATE || this == MODIFY;
    }

    /**
     * Check if this operation removes files.
     *
     * @return true for DELETE, false otherwise
     */
    public boolean isDelete() {
        return this == DELETE;
    }
}
