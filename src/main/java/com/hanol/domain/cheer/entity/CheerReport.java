package com.hanol.domain.cheer.entity;

import com.hanol.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 패치 신고 기록. 신고한 사람과 사유는 남기지 않는다. */
@Getter
@Entity
@Table(
    name = "reports",
    indexes =
        @Index(
            name = "idx_reports_sender_hash_reported_at",
            columnList = "sender_hash, reported_at"),
    uniqueConstraints = @UniqueConstraint(name = "uk_reports_cheer_id", columnNames = "cheer_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheerReport extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "cheer_id", nullable = false)
  private Cheer cheer;

  @Column(name = "sender_hash", nullable = false, length = 64)
  private String senderHash;

  @Column(name = "reported_at", nullable = false)
  private LocalDateTime reportedAt;

  @Builder
  private CheerReport(Cheer cheer, String senderHash, LocalDateTime reportedAt) {
    this.cheer = cheer;
    this.senderHash = senderHash;
    this.reportedAt = reportedAt;
  }
}
