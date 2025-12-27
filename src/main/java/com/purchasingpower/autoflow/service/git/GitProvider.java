package com.purchasingpower.autoflow.service.git;

/**
 * Enum representing supported Git providers.
 * ENTERPRISE: Supports multiple version control systems.
 */
public enum GitProvider {
    GITHUB("GitHub"),
    GITLAB("GitLab"),
    BITBUCKET("Bitbucket"),
    AZURE_DEVOPS("Azure DevOps"),
    GENERIC("Generic Git");

    private final String displayName;

    GitProvider(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
