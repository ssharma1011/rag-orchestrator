package com.purchasingpower.autoflow.service.impl;

import com.purchasingpower.autoflow.configuration.AppProperties;
import com.purchasingpower.autoflow.model.jira.JiraIssueDetails;
import com.purchasingpower.autoflow.service.JiraClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
public class JiraClientServiceImpl implements JiraClientService {

    private final WebClient webClient;

    public JiraClientServiceImpl(WebClient.Builder builder, AppProperties props) {
        String auth = props.getJira().getUsername() + ":" + props.getJira().getToken();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        this.webClient = builder
                .baseUrl(props.getJira().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Mono<JiraIssueDetails> getIssue(String issueKey) {
        log.debug("Fetching Jira issue details for {}", issueKey);
        return webClient.get()
                .uri("/rest/api/3/issue/{key}", issueKey)
                .retrieve()
                .bodyToMono(JiraIssueDetails.class)
                .doOnError(e -> log.error("Failed to fetch Jira {}", issueKey, e));
    }

    @Override
    public Mono<Void> addComment(String issueKey, String comment) {
        // Simple Atlassian Document Format (ADF) wrapper for comments
        Map<String, Object> adfBody = Map.of(
                "body", Map.of(
                        "type", "doc",
                        "version", 1,
                        "content", java.util.List.of(
                                Map.of(
                                        "type", "paragraph",
                                        "content", java.util.List.of(
                                                Map.of("type", "text", "text", comment)
                                        )
                                )
                        )
                )
        );

        return webClient.post()
                .uri("/rest/api/3/issue/{key}/comment", issueKey)
                .bodyValue(adfBody)
                .retrieve()
                .bodyToMono(Void.class);
    }
}
