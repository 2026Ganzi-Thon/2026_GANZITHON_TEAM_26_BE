package com.hanol.domain.mood.entity;

import com.hanol.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
    name = "mood_record_cards",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_mood_record_cards_mood_record_id_card_order",
            columnNames = {"mood_record_id", "card_order"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MoodRecordCard extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "mood_record_id", nullable = false)
  private MoodRecord moodRecord;

  @Column(name = "card_order", nullable = false)
  private int cardOrder;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "emotion", nullable = false, length = 30)
  private Emotion emotion;

  @Builder
  private MoodRecordCard(MoodRecord moodRecord, int cardOrder, Emotion emotion) {
    this.moodRecord = moodRecord;
    this.cardOrder = cardOrder;
    this.emotion = emotion;
  }
}
