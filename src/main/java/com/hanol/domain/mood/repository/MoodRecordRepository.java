package com.hanol.domain.mood.repository;

import com.hanol.domain.mood.entity.MoodRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoodRecordRepository extends JpaRepository<MoodRecord, Long> {}
