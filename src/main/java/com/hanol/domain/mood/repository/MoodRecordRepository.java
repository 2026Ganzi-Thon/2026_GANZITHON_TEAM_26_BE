package com.hanol.domain.mood.repository;

import com.hanol.domain.mood.entity.MoodRecord;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MoodRecordRepository extends JpaRepository<MoodRecord, Long> {

  @Query(
      "select distinct r.recordDate from MoodRecord r"
          + " where r.user.id = :userId and r.recordDate between :from and :to"
          + " order by r.recordDate")
  List<LocalDate> findRecordDates(
      @Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

  List<MoodRecord> findByUserIdAndRecordDateOrderByRecordedAtAsc(Long userId, LocalDate recordDate);
}
