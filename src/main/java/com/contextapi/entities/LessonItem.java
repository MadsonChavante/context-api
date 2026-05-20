package com.contextapi.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lesson_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LessonItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "context_id", nullable = false)
    private Context context;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String promptPt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String expectedAnswerEn;

    @Column(columnDefinition = "TEXT")
    private String lastAnswer;

    @Column(columnDefinition = "TEXT")
    private String teacherFeedback;

    private Integer score;

    @Column(nullable = false)
    private boolean completed = false;
}
