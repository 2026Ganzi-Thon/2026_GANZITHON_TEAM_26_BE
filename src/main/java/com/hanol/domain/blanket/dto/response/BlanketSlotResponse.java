package com.hanol.domain.blanket.dto.response;

import com.hanol.domain.blanket.entity.BlanketPiece;
import com.hanol.domain.cheer.entity.Cheer;
import com.hanol.domain.mood.entity.Emotion;
import java.time.LocalDateTime;

public record BlanketSlotResponse(
    String message,
    LocalDateTime receivedAt,
    boolean hasMoodRecordOnDate,
    int slotIndex,
    Long cheerId,
    boolean isDefault,
    Emotion emotion,
    LocalDateTime sentAt) {

  public static BlanketSlotResponse from(BlanketPiece piece, boolean hasMoodRecordOnDate) {
    Cheer cheer = piece.getCheer();
    return new BlanketSlotResponse(
        piece.getContent(),
        piece.getReceivedAt(),
        hasMoodRecordOnDate,
        piece.getSlotIndex(),
        cheer == null ? null : cheer.getId(),
        cheer == null,
        piece.getEmotion(),
        cheer == null ? null : cheer.getCreatedAt());
  }
}
