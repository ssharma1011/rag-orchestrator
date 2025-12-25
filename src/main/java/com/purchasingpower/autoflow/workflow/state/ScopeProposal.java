package com.purchasingpower.autoflow.workflow.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent's proposal of which files to modify/create.
 * Shown to developer for approval.
 * CRITICAL FIX: Ensure all Lists are never null for Jackson serialization
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScopeProposal implements Serializable {

    /**
     * Files to modify (existing files)
     */
    private List<FileAction> filesToModify;

    /**
     * Files to create (new files)
     */
    private List<FileAction> filesToCreate;

    /**
     * Test files to update/create
     */
    private List<FileAction> testsToUpdate;

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
    private List<String> risks;

    // ================================================================
    // CUSTOM GETTERS - GUARANTEE NON-NULL LISTS
    // ================================================================

    public List<FileAction> getFilesToModify() {
        if (filesToModify == null) {
            filesToModify = new ArrayList<>();
        }
        return filesToModify;
    }

    public List<FileAction> getFilesToCreate() {
        if (filesToCreate == null) {
            filesToCreate = new ArrayList<>();
        }
        return filesToCreate;
    }

    public List<FileAction> getTestsToUpdate() {
        if (testsToUpdate == null) {
            testsToUpdate = new ArrayList<>();
        }
        return testsToUpdate;
    }

    public List<String> getRisks() {
        if (risks == null) {
            risks = new ArrayList<>();
        }
        return risks;
    }

    /**
     * Total file count (for validation against MAX_FILES limit)
     */
    public int getTotalFileCount() {
        return getFilesToModify().size() + getFilesToCreate().size() + getTestsToUpdate().size();
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
                getFilesToModify().size(), formatFileList(getFilesToModify()),
                getFilesToCreate().size(), formatFileList(getFilesToCreate()),
                getTestsToUpdate().size(), formatFileList(getTestsToUpdate()),
                reasoning,
                estimatedComplexity,
                getRisks().isEmpty() ? "None identified" : String.join("\n", getRisks())
        );
    }

    private String formatFileList(List<FileAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return "  (none)";
        }
        return actions.stream()
                .map(a -> String.format("  - %s (%s)", a.getFilePath(), a.getReason()))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("  (none)");
    }

    /**
     * Builder customization to ensure default values
     */
    public static class ScopeProposalBuilder {
        public ScopeProposal build() {
            if (filesToModify == null) filesToModify = new ArrayList<>();
            if (filesToCreate == null) filesToCreate = new ArrayList<>();
            if (testsToUpdate == null) testsToUpdate = new ArrayList<>();
            if (risks == null) risks = new ArrayList<>();

            return new ScopeProposal(
                    filesToModify,
                    filesToCreate,
                    testsToUpdate,
                    reasoning,
                    estimatedComplexity,
                    risks
            );
        }
    }
}