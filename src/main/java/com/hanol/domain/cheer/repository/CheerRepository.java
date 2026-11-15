package com.hanol.domain.cheer.repository;

import com.hanol.domain.cheer.entity.Cheer;
import com.hanol.domain.cheer.entity.CheerStatus;
import com.hanol.domain.mood.entity.Emotion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheerRepository extends JpaRepository<Cheer, Long> {

  @Query(
      "select c.targetEmotion as emotion, count(c) as count from Cheer c"
          + " where c.status = :status group by c.targetEmotion")
  List<EmotionCount> countGroupByTargetEmotion(@Param("status") CheerStatus status);

  interface EmotionCount {

    Emotion getEmotion();

    long getCount();
  }
}
