package com.hanol.domain.user.repository;

import com.hanol.domain.user.entity.DailyStat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyStatRepository extends JpaRepository<DailyStat, Long> {

  Optional<DailyStat> findByUserIdAndStatDate(Long userId, LocalDate statDate);

  @Query("select max(d.lastSentAt) from DailyStat d where d.user.id = :userId")
  Optional<LocalDateTime> findLastSentAt(@Param("userId") Long userId);

  List<DailyStat> findByUserIdAndStatDateBetweenAndSentCountGreaterThan(
      Long userId, LocalDate from, LocalDate to, int sentCount);
}
