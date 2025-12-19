package com.purchasingpower.autoflow.model.conversation;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "CONVERSATION_CONTEXT")
@NoArgsConstructor
@AllArgsConstructor
public class ConversationContext {

    @Id
    private String issueKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationState state;

    @Column(nullable = false)
    private String repoName;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String requirements;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String codebaseAnalysis;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String approvedPlan;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "conversation_id")
    @OrderBy("timestamp ASC")
    private List<ConversationMessage> messages = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.lastUpdated = LocalDateTime.now();
    }

    public void addMessage(ConversationMessage message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        message.setTimestamp(LocalDateTime.now());
        this.messages.add(message);
    }
}
