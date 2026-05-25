package com.contextapi.repositories;

import com.contextapi.entities.LessonExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LessonExerciseRepository extends JpaRepository<LessonExercise, Long> {

    /** Recent exercises for a lesson (to avoid repeating patterns) */
    List<LessonExercise> findTop10ByLessonIdOrderByCreatedAtDesc(Long lessonId);

    /** All exercises for a lesson, ordered by creation date (avoids LazyInitializationException) */
    List<LessonExercise> findByLessonIdOrderByCreatedAtAsc(Long lessonId);

    /** Count exercises for a specific context within a lesson */
    int countByLessonIdAndContextId(Long lessonId, Long contextId);
}