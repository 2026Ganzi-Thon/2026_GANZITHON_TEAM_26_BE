package com.hanol.domain.mood.entity;

import com.hanol.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "stories",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_stories_mood_record_id",
            columnNames = {"mood_record_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Story extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "mood_record_id", nullable = false)
  private MoodRecord moodRecord;

  @Column(name = "content", nullable = false, length = 300)
  private String content;

  @Builder
  private Story(MoodRecord moodRecord, String content) {
    this.moodRecord = moodRecord;
    this.content = content;
  }
}
