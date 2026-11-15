package com.hanol.domain.cheer.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CheerContactMatcherTest {

  private final CheerContactMatcher matcher = new CheerContactMatcher();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "010-1234-5678로 연락해요",
        "01012345678",
        "010 1234 5678 연락 주세요",
        "010.1234.5678",
        "02-123-4567",
        "메일 patch.user@example.com 으로",
        "https://example.com 보세요",
        "HTTP://EXAMPLE.KR",
        "www.example.org 들어가 봐요",
        "example.com 검색",
        "blog.naver.com/abc",
        "patch.kr 놀러 와요"
      })
  @DisplayName("전화번호·이메일·URL 이 있으면 걸린다")
  void matchesContactOrLink(String content) {
    assertThat(matcher.matches(content)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "산책하면 좀 나아졌어요",
        "오늘도 고생했어요. 같이 버텨봐요",
        "하루 3번 10분씩 걸었어요",
        "2026년 11월에 많이 힘들었어요",
        "1234 5678",
        "제 인스타 @patch_123 놀러와요",
        "점 하나... 찍고 쉬어요",
        "커피.com이라는 말은 없어요"
      })
  @DisplayName("연락처·링크가 없으면 안 걸린다")
  void notMatchesPlainText(String content) {
    assertThat(matcher.matches(content)).isFalse();
  }
}
