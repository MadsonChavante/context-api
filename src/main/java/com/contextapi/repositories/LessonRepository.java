package com.contextapi.repositories;

import com.contextapi.entities.Lesson;
import com.contextapi.enums.LessonStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long> {
    Optional<Lesson> findFirstByStatusOrderByCreatedAtDesc(LessonStatus status);
}
