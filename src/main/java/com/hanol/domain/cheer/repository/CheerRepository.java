package com.hanol.domain.cheer.repository;

import com.hanol.domain.cheer.entity.Cheer;
import com.hanol.domain.cheer.entity.CheerStatus;
import com.hanol.domain.mood.entity.Emotion;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheerRepository extends JpaRepository<Cheer, Long> {

  @Query(
      "select c.targetEmotion as emotion, count(c) as count from Cheer c"
          + " where c.status = :status group by c.targetEmotion")
  List<EmotionCount> countGroupByTargetEmotion(@Param("status") CheerStatus status);

  boolean existsByTargetEmotionAndStatusAndSenderHashNot(
      Emotion targetEmotion, CheerStatus status, String senderHash);

  /** 대기열에서 오래 기다린 순. 본인이 보낸 패치는 제외한다. */
  @Query(
      "select c from Cheer c"
          + " where c.targetEmotion = :emotion and c.status = :status"
          + " and c.senderHash <> :senderHash"
          + " order by c.createdAt asc, c.id asc")
  List<Cheer> findQueueCandidates(
      @Param("emotion") Emotion emotion,
      @Param("status") CheerStatus status,
      @Param("senderHash") String senderHash,
      Pageable pageable);

  /** 아직 from 상태일 때만 to 로 바꾼다. 1이면 선점 성공. 영속성 컨텍스트는 비우지 않는다. */
  @Modifying
  @Query(
      "update Cheer c set c.status = :to, c.updatedAt = :now"
          + " where c.id = :id and c.status = :from")
  int changeStatusIf(
      @Param("id") Long id,
      @Param("from") CheerStatus from,
      @Param("to") CheerStatus to,
      @Param("now") LocalDateTime now);

  interface EmotionCount {

    Emotion getEmotion();

    long getCount();
  }
}
