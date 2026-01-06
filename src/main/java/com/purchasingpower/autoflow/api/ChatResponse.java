package com.purchasingpower.autoflow.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Response from chat endpoint.
 *
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private boolean success;
    private String conversationId;
    private String response;
    private String error;

    @Builder.Default
    private List<Citation> citations = new ArrayList<>();

    @Builder.Default
    private List<SuggestedAction> actions = new ArrayList<>();

    public static ChatResponse success(String conversationId, String response) {
        return ChatResponse.builder()
            .success(true)
            .conversationId(conversationId)
            .response(response)
            .build();
    }

    public static ChatResponse error(String error) {
        return ChatResponse.builder()
            .success(false)
            .error(error)
            .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Citation {
        private String filePath;
        private int lineNumber;
        private String snippet;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuggestedAction {
        private String action;
        private String label;
    }
}
