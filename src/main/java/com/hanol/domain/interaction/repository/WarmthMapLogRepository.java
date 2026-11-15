package com.hanol.domain.interaction.repository;

import com.hanol.domain.interaction.entity.WarmthMapLog;
import com.hanol.domain.mood.entity.Emotion;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarmthMapLogRepository extends JpaRepository<WarmthMapLog, Long> {

  boolean existsByUserIdAndEmotionAndLoggedAtAfter(
      Long userId, Emotion emotion, LocalDateTime loggedAt);
}
