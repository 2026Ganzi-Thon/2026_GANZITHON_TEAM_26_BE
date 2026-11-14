package com.hanol.domain.mood.repository;

import com.hanol.domain.mood.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryRepository extends JpaRepository<Story, Long> {}
