package com.contextapi.dtos;

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
    private String dynamicType;
    private LessonStatus status;
    private String intro;
    private String finalFeedback;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private int exerciseCount;
    /** Current exercise (if exists and unanswered) */
    private ExerciseDTO currentExercise;
    /** The teacher's last message (feedback on last answer, or answer to a doubt) */
    private String lastTeacherMessage;
    /** Whether the last interaction was a doubt (question), not a translation */
    private boolean lastWasDoubt;
    /** Per-context statistics */
    private List<ContextStatsDTO> contextStats;
}
