package com.purchasingpower.autoflow.workflow.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * File uploaded by user (logs, screenshots, etc.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUpload {

    private String fileName;
    private String filePath;  // Where it's stored temporarily
    private FileType fileType;
    private long sizeBytes;
    private long uploadedAt;

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