package com.hanol.domain.cheer.entity;

import com.hanol.domain.mood.entity.Emotion;
import com.hanol.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 패치. 보낸 사람은 sender_hash(HMAC)로만 남긴다. */
@Getter
@Entity
@Table(
    name = "cheers",
    indexes =
        @Index(
            name = "idx_cheers_target_emotion_status_created_at",
            columnList = "target_emotion, status, created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cheer extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "sender_hash", nullable = false, length = 64)
  private String senderHash;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "target_emotion", nullable = false, length = 30)
  private Emotion targetEmotion;

  @Column(name = "content", nullable = false, length = 50)
  private String content;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "status", nullable = false, length = 20)
  private CheerStatus status;

  @Builder
  private Cheer(String senderHash, Emotion targetEmotion, String content) {
    this.senderHash = senderHash;
    this.targetEmotion = targetEmotion;
    this.content = content;
    this.status = CheerStatus.WAITING;
  }
}
