package com.hanol.domain.cheer.service;

import com.hanol.domain.mood.entity.Emotion;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/** AI 키가 없을 때는 대체 문구만 쓴다. */
@RequiredArgsConstructor
public class StubDefaultCheerMessageGenerator implements DefaultCheerMessageGenerator {

  private final CheerFallbackMessages fallbackMessages;

  @Override
  public String generate(Emotion emotion, Set<String> receivedMessages) {
    return fallbackMessages.pick(emotion, receivedMessages);
  }
}
