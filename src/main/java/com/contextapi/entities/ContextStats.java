package com.contextapi.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Accumulated statistics for a specific context within a lesson.
 * Updated each time the student answers an exercise about this context.
 */
@Entity
@Table(name = "context_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContextStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "context_id", nullable = false)
    private Context context;

    /** Total number of exercises answered for this context */
    @Column(nullable = false)
    private int totalExercises = 0;

    /** Sum of scores (0-100 each) */
    @Column(nullable = false)
    private int totalScore = 0;

    public double getAverageScore() {
        return totalExercises > 0 ? (double) totalScore / totalExercises : 0;
    }

    public void addExercise(int score) {
        this.totalExercises++;
        this.totalScore += Math.max(0, Math.min(100, score));
    }
}