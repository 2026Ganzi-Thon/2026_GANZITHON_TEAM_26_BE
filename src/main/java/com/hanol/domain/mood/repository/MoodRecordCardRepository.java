package com.hanol.domain.mood.repository;

import com.hanol.domain.mood.entity.MoodRecordCard;
import com.hanol.domain.mood.entity.ReceiveStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoodRecordCardRepository extends JpaRepository<MoodRecordCard, Long> {

  List<MoodRecordCard> findByMoodRecordIdInOrderByCardOrderAsc(Collection<Long> moodRecordIds);

  List<MoodRecordCard> findByMoodRecordIdOrderByCardOrderAsc(Long moodRecordId);

  void deleteByMoodRecordId(Long moodRecordId);

  /** 오늘 기록 중 아직 받기 처리되지 않은(NOT_RECEIVED) 도안을 제외하고 하나라도 있으면 '패치 받기 완료'로 본다. */
  boolean existsByMoodRecordUserIdAndMoodRecordRecordDateAndReceiveStatusNot(
      Long userId, LocalDate recordDate, ReceiveStatus receiveStatus);
}
