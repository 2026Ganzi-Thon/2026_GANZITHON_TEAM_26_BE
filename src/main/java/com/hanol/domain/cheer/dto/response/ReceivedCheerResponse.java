package com.hanol.domain.cheer.dto.response;

import com.hanol.domain.mood.entity.Emotion;

public record ReceivedCheerResponse(
    Long cheerId, Emotion emotion, String message, boolean isDefault) {

  public static ReceivedCheerResponse ofCheer(Long cheerId, Emotion emotion, String message) {
    return new ReceivedCheerResponse(cheerId, emotion, message, false);
  }

  public static ReceivedCheerResponse ofDefault(Emotion emotion, String message) {
    return new ReceivedCheerResponse(null, emotion, message, true);
  }
}
