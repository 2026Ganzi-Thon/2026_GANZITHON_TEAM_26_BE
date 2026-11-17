package com.hanol.domain.cheer.dto.response;

import com.hanol.domain.mood.entity.Emotion;

public record ReceivedCheerResponse(
    Long cheerId,
    Emotion emotion,
    String message,
    boolean isDefault,
    Integer slotIndex,
    boolean blanketCompleted) {

  public static ReceivedCheerResponse ofCheer(
      Long cheerId, Emotion emotion, String message, Integer slotIndex, boolean blanketCompleted) {
    return new ReceivedCheerResponse(cheerId, emotion, message, false, slotIndex, blanketCompleted);
  }

  public static ReceivedCheerResponse ofDefault(
      Emotion emotion, String message, Integer slotIndex, boolean blanketCompleted) {
    return new ReceivedCheerResponse(null, emotion, message, true, slotIndex, blanketCompleted);
  }

  /** 하루 4번째 이상 기록의 기본 위로. 담요에 쌓이지 않아 slotIndex 가 없다. */
  public static ReceivedCheerResponse ofDefault(Emotion emotion, String message) {
    return ofDefault(emotion, message, null, false);
  }
}
