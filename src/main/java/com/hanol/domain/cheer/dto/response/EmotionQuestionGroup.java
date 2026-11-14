package com.hanol.domain.cheer.dto.response;

import com.hanol.domain.mood.entity.Emotion;
import java.util.List;

public record EmotionQuestionGroup(Emotion emotion, List<String> questions) {

  public static EmotionQuestionGroup from(Emotion emotion) {
    return new EmotionQuestionGroup(emotion, List.of(emotion.getCheerQuestion()));
  }
}
