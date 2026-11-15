package com.hanol.domain.mood.repository;

import com.hanol.domain.mood.entity.MoodRecordCard;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoodRecordCardRepository extends JpaRepository<MoodRecordCard, Long> {

  List<MoodRecordCard> findByMoodRecordIdInOrderByCardOrderAsc(Collection<Long> moodRecordIds);

  List<MoodRecordCard> findByMoodRecordIdOrderByCardOrderAsc(Long moodRecordId);

  void deleteByMoodRecordId(Long moodRecordId);
}
