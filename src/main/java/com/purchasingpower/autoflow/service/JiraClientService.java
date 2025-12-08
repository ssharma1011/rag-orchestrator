package com.purchasingpower.autoflow.service;

import com.purchasingpower.autoflow.model.jira.JiraIssueDetails;
import reactor.core.publisher.Mono;

public interface JiraClientService {
    Mono<JiraIssueDetails> getIssue(String issueKey);

    Mono<Void> addComment(String issueKey, String comment);
}
