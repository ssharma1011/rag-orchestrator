package com.purchasingpower.autoflow.workflow.state;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Single message in conversation.
 *
 * Contains proper Jackson annotations for LocalDateTime serialization/deserialization
 * to prevent conversation history loss on database reload.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage implements Serializable {

    /**
     * Role of the message sender.
     * Values: "user", "assistant", "system"
     */
    private String role;

    /**
     * The message content.
     */
    private String content;

    /**
     * When the message was created.
     * Uses Jackson JSR310 annotations to properly handle LocalDateTime serialization.
     */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS")
    private LocalDateTime timestamp;
}