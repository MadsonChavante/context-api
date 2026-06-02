package com.contextapi.repositories;

import com.contextapi.entities.ContextStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContextStatsRepository extends JpaRepository<ContextStats, Long> {
    Optional<ContextStats> findByContextId(Long contextId);
}
