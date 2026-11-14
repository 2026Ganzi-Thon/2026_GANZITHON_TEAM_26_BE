package com.hanol.domain.mood.entity;

import com.hanol.domain.user.entity.User;
import com.hanol.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "mood_records",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_mood_records_user_id_record_date_daily_seq",
            columnNames = {"user_id", "record_date", "daily_seq"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MoodRecord extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "record_date", nullable = false)
  private LocalDate recordDate;

  @Column(name = "recorded_at", nullable = false)
  private LocalDateTime recordedAt;

  /** 같은 날 몇 번째 기록인지 (1부터). */
  @Column(name = "daily_seq", nullable = false)
  private int dailySeq;

  @Builder
  private MoodRecord(User user, LocalDateTime recordedAt, int dailySeq) {
    this.user = user;
    this.recordDate = recordedAt.toLocalDate();
    this.recordedAt = recordedAt;
    this.dailySeq = dailySeq;
  }
}
