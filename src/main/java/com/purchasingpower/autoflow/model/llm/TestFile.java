package com.purchasingpower.autoflow.model.llm;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestFile {
    private String path;
    private String content;
}
