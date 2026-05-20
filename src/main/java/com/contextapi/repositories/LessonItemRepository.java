package com.contextapi.repositories;

import com.contextapi.entities.LessonItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LessonItemRepository extends JpaRepository<LessonItem, Long> {
}
