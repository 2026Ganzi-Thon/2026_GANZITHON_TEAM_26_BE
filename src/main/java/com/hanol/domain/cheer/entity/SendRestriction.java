package com.hanol.domain.cheer.entity;

import com.hanol.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** BLK-RPT-002 보내기 제한. 발신자 해시마다 1행, 해제 시각이 지나면 다시 보낼 수 있다. */
@Getter
@Entity
@Table(
    name = "send_restrictions",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_send_restrictions_sender_hash", columnNames = "sender_hash"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SendRestriction extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "sender_hash", nullable = false, length = 64)
  private String senderHash;

  @Column(name = "restricted_until")
  private LocalDateTime restrictedUntil;

  @Builder
  private SendRestriction(String senderHash) {
    this.senderHash = senderHash;
  }

  public void restrictUntil(LocalDateTime restrictedUntil) {
    this.restrictedUntil = restrictedUntil;
  }

  public boolean isRestrictedAt(LocalDateTime now) {
    return restrictedUntil != null && restrictedUntil.isAfter(now);
  }
}
