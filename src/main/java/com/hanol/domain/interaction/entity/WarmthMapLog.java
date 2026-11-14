package com.hanol.domain.interaction.entity;

import com.hanol.domain.mood.entity.Emotion;
import com.hanol.domain.user.entity.User;
import com.hanol.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
    name = "warmth_map_logs",
    indexes = {
      @Index(name = "idx_warmth_map_logs_emotion_logged_at", columnList = "emotion, logged_at"),
      @Index(
          name = "idx_warmth_map_logs_user_id_emotion_logged_at",
          columnList = "user_id, emotion, logged_at")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WarmthMapLog extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "emotion", nullable = false, length = 30)
  private Emotion emotion;

  @Column(name = "logged_at", nullable = false)
  private LocalDateTime loggedAt;

  @Builder
  private WarmthMapLog(User user, Emotion emotion, LocalDateTime loggedAt) {
    this.user = user;
    this.emotion = emotion;
    this.loggedAt = loggedAt;
  }
}
