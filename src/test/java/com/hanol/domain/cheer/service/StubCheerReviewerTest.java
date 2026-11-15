package com.hanol.domain.cheer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanol.domain.alert.service.CrisisKeywordMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StubCheerReviewerTest {

  private final StubCheerReviewer reviewer =
      new StubCheerReviewer(new CrisisKeywordMatcher(), new CheerContactMatcher());

  @Test
  @DisplayName("위험 단어가 있으면 CRISIS")
  void keywordIsCrisis() {
    assertThat(reviewer.review("요즘도 죽고 싶어요")).isEqualTo(CheerReviewResult.CRISIS);
  }

  @Test
  @DisplayName("위험 단어와 연락처가 같이 있으면 CRISIS")
  void keywordWithContactIsCrisis() {
    assertThat(reviewer.review("죽고 싶으면 010-1234-5678")).isEqualTo(CheerReviewResult.CRISIS);
  }

  @Test
  @DisplayName("연락처·링크가 있으면 CONTACT_OR_LINK")
  void contactIsRejected() {
    assertThat(reviewer.review("010-1234-5678로 연락해요")).isEqualTo(CheerReviewResult.CONTACT_OR_LINK);
  }

  @Test
  @DisplayName("나머지는 NORMAL")
  void othersAreNormal() {
    assertThat(reviewer.review("ㅅㅂ 다 지나가")).isEqualTo(CheerReviewResult.NORMAL);
  }
}
