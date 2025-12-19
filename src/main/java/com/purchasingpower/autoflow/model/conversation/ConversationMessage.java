package com.purchasingpower.autoflow.model.conversation;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "CONVERSATION_MESSAGES")
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String role; // "user" or "assistant"

    @Lob
    @Column(columnDefinition = "CLOB", nullable = false)
    private String content;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public ConversationMessage(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }
}