package com.purchasingpower.autoflow.core;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents an indexed code repository.
 *
 * @since 2.0.0
 */
public interface Repository {

    String getId();

    String getUrl();

    String getBranch();

    RepositoryType getType();

    String getLanguage();

    String getDomain();

    LocalDateTime getLastIndexedAt();

    String getLastIndexedCommit();

    Map<String, String> getMetadata();
}
