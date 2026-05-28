package com.contextapi.entities;

import com.contextapi.enums.LessonStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lessons")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LessonStatus status = LessonStatus.IN_PROGRESS;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime completedAt;

    @Column(nullable = false)
    private int exerciseCount = 0;

    @ElementCollection
    @CollectionTable(name = "lesson_conversation", joinColumns = @JoinColumn(name = "lesson_id"))
    @OrderColumn(name = "message_order")
    private List<ConversationMessage> conversationHistory = new ArrayList<>();
}

