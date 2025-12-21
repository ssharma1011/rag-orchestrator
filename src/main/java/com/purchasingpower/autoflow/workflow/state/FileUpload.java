package com.purchasingpower.autoflow.workflow.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * File uploaded by user (logs, screenshots, etc.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUpload implements Serializable {

    private String fileName;
    private String filePath;  // Where it's stored temporarily
    private String contentType; // Added to support controller's setContentType
    private FileType fileType;
    private long sizeBytes;
    private long uploadedAt;

    // Added size field to match controller's .size() call if strictly needed,
    // or we map controller's size() to sizeBytes via builder manually.
    // To make controller compile without changing it too much, I'll add 'size' alias
    // or just rely on the controller using the builder for 'sizeBytes'.

    // For the controller compilation:
    // Controller calls: .size(file.getSize()) -> expects 'size' field or 'size' method in builder.
    // Your class has 'sizeBytes'. I will align the controller to use 'sizeBytes'.

    public enum FileType {
        LOG,
        SCREENSHOT,
        DOCUMENT,
        OTHER
    }

    /**
     * Determine file type from extension
     */
    public static FileType detectType(String fileName) {
        if (fileName == null) return FileType.OTHER;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".log") || lower.endsWith(".txt")) {
            return FileType.LOG;
        } else if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return FileType.SCREENSHOT;
        } else if (lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return FileType.DOCUMENT;
        }
        return FileType.OTHER;
    }
}