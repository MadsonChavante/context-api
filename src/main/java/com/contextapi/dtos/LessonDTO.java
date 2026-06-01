package com.contextapi.dtos;

import com.contextapi.entities.ConversationMessage;
import com.contextapi.enums.LessonStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LessonDTO {
    private Long id;
    private LessonStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private List<ConversationMessage> conversationHistory;
}

