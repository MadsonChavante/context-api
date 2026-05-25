package com.contextapi.entities;

import com.contextapi.enums.LessonStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Continuous lesson — no fixed item set.
 * The teacher (AI) generates one exercise at a time.
 * The student can stay as long as they want; each context gets stats.
 */
@Entity
@Table(name = "lessons")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Always TRANSLATION_REPETITION for now */
    @Column(nullable = false)
    private String dynamicType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LessonStatus status = LessonStatus.IN_PROGRESS;

    @Column(columnDefinition = "TEXT")
    private String intro;

    @Column(columnDefinition = "TEXT")
    private String finalFeedback;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime completedAt;

    /** Total exercises answered in this lesson */
    @Column(nullable = false)
    private int exerciseCount = 0;

    /**
     * Comma-separated list of context IDs to prioritize.
     * Updated based on stats — contexts with lower scores get priority.
     */
    @Column(columnDefinition = "TEXT")
    private String preferredContextIds;

    /**
     * All exercises generated in this lesson.
     * Used to avoid repeating the same variation.
     */
    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<LessonExercise> exercises = new ArrayList<>();

    /**
     * Per-context statistics to track progress.
     */
    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ContextStats> contextStats = new ArrayList<>();

    @Version
    private Long version;

    public void addExercise(LessonExercise exercise) {
        exercise.setLesson(this);
        exercises.add(exercise);
        exerciseCount++;
    }
}
