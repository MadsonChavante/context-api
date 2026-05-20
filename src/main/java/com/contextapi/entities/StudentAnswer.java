package com.contextapi.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_answers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_item_id", nullable = false)
    private LessonItem lessonItem;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answerText;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    private Integer score;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
