package com.contextapi.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents one exercise/question in a continuous lesson.
 * Generated on demand by AI — the lesson has no fixed set of exercises.
 */
@Entity
@Table(name = "lesson_exercises")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LessonExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "context_id", nullable = false)
    private Context context;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String promptPt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String expectedAnswerEn;

    @Column(columnDefinition = "TEXT")
    private String variationNote;

    @Column(columnDefinition = "TEXT")
    private String studentAnswer;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    private Integer score;

    /**
     * Whether this exercise has been answered.
     * An exercise can be created (waiting for answer) or answered.
     */
    @Column(nullable = false)
    private boolean answered = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}