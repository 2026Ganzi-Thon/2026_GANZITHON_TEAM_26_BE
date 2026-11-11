package com.hanol.domain.user.entity;

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
    name = "daily_stats",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_daily_stats_user_id_stat_date",
            columnNames = {"user_id", "stat_date"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyStat extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "stat_date", nullable = false)
  private LocalDate statDate;

  @Column(name = "pick_count", nullable = false)
  private int pickCount;

  @Column(name = "sent_count", nullable = false)
  private int sentCount;

  @Column(name = "bonus_send_count", nullable = false)
  private int bonusSendCount;

  @Column(name = "last_sent_at")
  private LocalDateTime lastSentAt;

  @Builder
  private DailyStat(User user, LocalDate statDate) {
    this.user = user;
    this.statDate = statDate;
    this.pickCount = 0;
    this.sentCount = 0;
    this.bonusSendCount = 0;
  }
}
