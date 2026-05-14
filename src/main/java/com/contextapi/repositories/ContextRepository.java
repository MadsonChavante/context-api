package com.contextapi.repositories;

import com.contextapi.entities.Context;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContextRepository extends JpaRepository<Context, Long> {
}
