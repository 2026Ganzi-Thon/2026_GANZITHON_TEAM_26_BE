package com.hanol.domain.mood.repository;

import com.hanol.domain.mood.entity.Story;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoryRepository extends JpaRepository<Story, Long> {

  @Query("select s.moodRecord.id from Story s where s.moodRecord.id in :moodRecordIds")
  List<Long> findMoodRecordIds(@Param("moodRecordIds") Collection<Long> moodRecordIds);

  Optional<Story> findByMoodRecordId(Long moodRecordId);

  void deleteByMoodRecordId(Long moodRecordId);
}
