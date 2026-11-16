package com.hanol.domain.interaction.repository;

import com.hanol.domain.interaction.entity.WarmthMapLog;
import com.hanol.domain.mood.entity.Emotion;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarmthMapLogRepository extends JpaRepository<WarmthMapLog, Long> {

  boolean existsByUserIdAndEmotionAndLoggedAtAfter(
      Long userId, Emotion emotion, LocalDateTime loggedAt);

  /** [from, to) 구간에서 해당 감정을 고른, 본인을 제외한 유저 수(중복 제거). */
  @Query(
      "select count(distinct w.user.id) from WarmthMapLog w"
          + " where w.emotion = :emotion and w.loggedAt >= :from and w.loggedAt < :to"
          + " and w.user.id <> :excludeUserId")
  long countOtherUsers(
      @Param("emotion") Emotion emotion,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      @Param("excludeUserId") Long excludeUserId);
}
