package com.hanol.domain.user.repository;

import com.hanol.domain.user.entity.DailyStat;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyStatRepository extends JpaRepository<DailyStat, Long> {

  Optional<DailyStat> findByUserIdAndStatDate(Long userId, LocalDate statDate);
}
