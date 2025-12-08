package com.purchasingpower.autoflow.service.impl;


import com.purchasingpower.autoflow.configuration.AppProperties;
import com.purchasingpower.autoflow.service.BitbucketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
public class BitbucketServiceImpl implements BitbucketService {

    private final WebClient webClient;
    private final AppProperties props;

    public BitbucketServiceImpl(WebClient.Builder builder, AppProperties props) {
        this.props = props;

        // Construct Basic Auth Header
        /*String auth = props.getBitbucket().getApiUsername() + ":" + props.getBitbucket().getAppPassword();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        this.webClient = builder
                .baseUrl(props.getBitbucket().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();*/

        String token = props.getBitbucket().getAppPassword();

        this.webClient = builder
                .baseUrl(props.getBitbucket().getBaseUrl())
                // ✅ Header format: "Authorization: Bearer ATCTT..."
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

    }

    @Override
    public String createPullRequest(String repoSlug, String sourceBranch, String destinationBranch, String title) {
        log.info("Creating PR: {} -> {} in repo {}", sourceBranch, destinationBranch, repoSlug);

        // Bitbucket API v2 Payload
        Map<String, Object> body = Map.of(
                "title", title,
                "source", Map.of("branch", Map.of("name", sourceBranch)),
                "destination", Map.of("branch", Map.of("name", destinationBranch))
        );

        String workspace = props.getBitbucket().getWorkspace();

        try {
            Map response = webClient.post()
                    .uri("/repositories/{workspace}/{repo}/pullrequests", workspace, repoSlug)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            // Extract the URL from the response
            if (response != null && response.containsKey("links")) {
                Map links = (Map) response.get("links");
                if (links.containsKey("html")) {
                    Map htmlLink = (Map) links.get("html");
                    return htmlLink.get("href").toString();
                }
            }
            return "PR Created (URL lookup failed)";

        } catch (Exception e) {
            log.error("Failed to create Bitbucket PR", e);
            throw new RuntimeException("Bitbucket API Error: " + e.getMessage(), e);
        }
    }
}