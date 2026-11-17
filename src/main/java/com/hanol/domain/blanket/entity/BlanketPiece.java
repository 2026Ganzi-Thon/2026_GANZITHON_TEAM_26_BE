package com.hanol.domain.blanket.entity;

import com.hanol.domain.cheer.entity.Cheer;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 담요 한 칸. 기본 메시지면 cheer 가 null 이다. */
@Getter
@Entity
@Table(
    name = "blanket_pieces",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_blanket_pieces_blanket_id_slot_index",
            columnNames = {"blanket_id", "slot_index"}),
    indexes = @Index(name = "idx_blanket_pieces_cheer_id", columnList = "cheer_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlanketPiece extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "blanket_id", nullable = false)
  private Blanket blanket;

  /** 1~30. */
  @Column(name = "slot_index", nullable = false)
  private int slotIndex;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cheer_id")
  private Cheer cheer;

  @Column(name = "content", nullable = false, length = 50)
  private String content;

  @Column(name = "received_at", nullable = false)
  private LocalDateTime receivedAt;

  @Builder
  private BlanketPiece(
      Blanket blanket, int slotIndex, Cheer cheer, String content, LocalDateTime receivedAt) {
    this.blanket = blanket;
    this.slotIndex = slotIndex;
    this.cheer = cheer;
    this.content = content;
    this.receivedAt = receivedAt;
  }

  /** 신고로 숨겨진 패치를 새 패치(또는 기본 메시지면 cheer null)로 바꾼다. 칸 위치·받은 시각은 그대로. */
  public void replace(Cheer cheer, String content) {
    this.cheer = cheer;
    this.content = content;
  }
}
