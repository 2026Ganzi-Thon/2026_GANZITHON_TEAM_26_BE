package com.hanol.domain.alert.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CrisisKeywordMatcherTest {

  private final CrisisKeywordMatcher matcher = new CrisisKeywordMatcher();

  @ParameterizedTest
  @ValueSource(strings = {"죽고 싶어", "죽 고 싶 어", "죽.고.싶.다", "자살하고 싶다", "I want to die"})
  @DisplayName("띄어쓰기·기호를 섞어도 위험 단어는 걸린다")
  void matchCrisisKeyword(String content) {
    assertThat(matcher.matches(content)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"혼자 살고 싶어", "죽을 맛이야", "오늘 너무 피곤해"})
  @DisplayName("위험 단어가 없는 일상 표현은 걸리지 않는다")
  void ignoreNonCrisisContent(String content) {
    assertThat(matcher.matches(content)).isFalse();
  }
}
