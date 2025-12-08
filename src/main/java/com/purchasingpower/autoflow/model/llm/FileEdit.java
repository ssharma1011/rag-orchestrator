package com.purchasingpower.autoflow.model.llm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileEdit {
    private String path;
    private String op; // "create", "modify", "delete"
    private String content;
}