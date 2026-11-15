package com.hanol.domain.blanket.entity;

import com.hanol.domain.user.entity.User;
import com.hanol.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 담요. 30칸이 모두 차면 completed_at 이 기록되고, 다음 패치부터는 새 담요에 쌓인다. */
@Getter
@Entity
@Table(
    name = "blankets",
    indexes =
        @Index(name = "idx_blankets_user_id_completed_at", columnList = "user_id, completed_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Blanket extends BaseTimeEntity {

  public static final int SLOT_COUNT = 30;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Builder
  private Blanket(User user) {
    this.user = user;
  }

  public void complete(LocalDateTime now) {
    this.completedAt = now;
  }

  public boolean isCompleted() {
    return completedAt != null;
  }
}
