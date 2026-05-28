package com.contextapi.entities;

import com.contextapi.enums.ConversationAuthor;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessage {

    @Enumerated(EnumType.STRING)
    @Column(name = "author", nullable = false, length = 20)
    private ConversationAuthor author;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MessageType type;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "context_id")
    private Long contextId;

    public enum MessageType {
        EXERCISE,
        ANSWER,
        FEEDBACK,
        DOUBT,
        GREETING
    }

}