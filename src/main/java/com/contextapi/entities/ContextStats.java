package com.contextapi.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "context_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContextStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "context_id", nullable = false)
    private Context context;

    @Column(nullable = false)
    private int totalExercises = 0;
    
    @Column(nullable = false)
    private int totalScore = 0;

    public double getAverageScore() {
        return totalExercises > 0 ? (double) totalScore / totalExercises : 0;
    }

    public void addExercise(int score) {
        this.totalExercises++;
        this.totalScore += Math.clamp(score, 0, 100);
    }
}
