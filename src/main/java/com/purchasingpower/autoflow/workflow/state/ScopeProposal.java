package com.purchasingpower.autoflow.workflow.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent's proposal of which files to modify/create.
 * Shown to developer for approval.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScopeProposal {

    /**
     * Files to modify (existing files)
     */
    @Builder.Default
    private List<FileAction> filesToModify = new ArrayList<>();

    /**
     * Files to create (new files)
     */
    @Builder.Default
    private List<FileAction> filesToCreate = new ArrayList<>();

    /**
     * Test files to update/create
     */
    @Builder.Default
    private List<FileAction> testsToUpdate = new ArrayList<>();

    /**
     * Explanation of why these files
     */
    private String reasoning;

    /**
     * Estimated complexity (1-10 scale)
     * 1-3: Simple
     * 4-7: Medium
     * 8-10: Complex
     */
    private int estimatedComplexity;

    /**
     * Potential risks
     */
    @Builder.Default
    private List<String> risks = new ArrayList<>();

    /**
     * Total file count (for validation against MAX_FILES limit)
     */
    public int getTotalFileCount() {
        return filesToModify.size() + filesToCreate.size() + testsToUpdate.size();
    }

    /**
     * Format for display to user
     */
    public String formatForApproval() {
        return String.format("""
            📋 **Scope Proposal**
            
            **Modify (%d files):**
            %s
            
            **Create (%d files):**
            %s
            
            **Update Tests (%d files):**
            %s
            
            **Reasoning:**
            %s
            
            **Estimated Complexity:** %d/10
            
            **Risks:**
            %s
            
            ✅ **Approve?** (yes/no/modify)
            """,
                filesToModify.size(), formatFileList(filesToModify),
                filesToCreate.size(), formatFileList(filesToCreate),
                testsToUpdate.size(), formatFileList(testsToUpdate),
                reasoning,
                estimatedComplexity,
                risks.isEmpty() ? "None identified" : String.join("\n", risks)
        );
    }

    private String formatFileList(List<FileAction> actions) {
        if (actions.isEmpty()) {
            return "  (none)";
        }
        return actions.stream()
                .map(a -> String.format("  - %s (%s)", a.getFilePath(), a.getReason()))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("  (none)");
    }
}