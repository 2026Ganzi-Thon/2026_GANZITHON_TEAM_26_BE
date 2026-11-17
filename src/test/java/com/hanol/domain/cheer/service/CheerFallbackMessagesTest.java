package com.hanol.domain.cheer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanol.domain.alert.service.CrisisKeywordMatcher;
import com.hanol.domain.mood.entity.Emotion;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CheerFallbackMessagesTest {

  private static final int REPEAT = 50;

  private final CheerFallbackMessages fallbackMessages = new CheerFallbackMessages();

  @ParameterizedTest
  @EnumSource(Emotion.class)
  @DisplayName("감정마다 서로 다른 50자 이내 문구가 3개씩 있다")
  void threeMessagesPerEmotion(Emotion emotion) {
    List<String> messages = fallbackMessages.of(emotion);

    assertThat(messages).hasSize(3).doesNotHaveDuplicates();
    assertThat(messages)
        .allSatisfy(message -> assertThat(message).isNotBlank().hasSizeLessThanOrEqualTo(50));
  }

  @Test
  @DisplayName("대체 문구 33개가 모두 기본 메시지 규칙 검사를 통과한다")
  void allMessagesPassRuleCheck() {
    AiDefaultCheerMessageGenerator generator =
        new AiDefaultCheerMessageGenerator(
            new CrisisKeywordMatcher(), new CheerContactMatcher(), null, null, fallbackMessages);
    List<String> all =
        Arrays.stream(Emotion.values()).flatMap(e -> fallbackMessages.of(e).stream()).toList();

    assertThat(all)
        .hasSize(33)
        .allSatisfy(message -> assertThat(generator.rejectReason(message, Set.of())).isNull());
  }

  @Test
  @DisplayName("이미 받은 문구는 빼고 고른다")
  void excludesReceived() {
    List<String> messages = fallbackMessages.of(Emotion.TEARFUL);
    Set<String> received = Set.of(messages.get(0), messages.get(1));

    Set<String> picked = new HashSet<>();
    for (int i = 0; i < REPEAT; i++) {
      picked.add(fallbackMessages.pick(Emotion.TEARFUL, received));
    }

    assertThat(picked).containsExactly(messages.get(2));
  }

  @Test
  @DisplayName("3개를 다 받았으면 그래도 그중 하나를 준다")
  void picksAnyWhenAllReceived() {
    List<String> messages = fallbackMessages.of(Emotion.UNSURE);

    assertThat(fallbackMessages.pick(Emotion.UNSURE, Set.copyOf(messages))).isIn(messages);
  }
}
