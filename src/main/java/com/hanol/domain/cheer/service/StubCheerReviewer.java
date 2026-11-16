package com.hanol.domain.cheer.service;

import com.hanol.domain.alert.service.CrisisKeywordMatcher;
import lombok.RequiredArgsConstructor;

/** AI 키가 없을 때 쓰는 규칙 검수. */
@RequiredArgsConstructor
public class StubCheerReviewer implements CheerReviewer {

  private final CrisisKeywordMatcher keywordMatcher;
  private final CheerContactMatcher contactMatcher;

  @Override
  public CheerReviewResult review(String content) {
    if (keywordMatcher.matches(content)) {
      return CheerReviewResult.CRISIS;
    }
    if (contactMatcher.matches(content)) {
      return CheerReviewResult.CONTACT_OR_LINK;
    }
    return CheerReviewResult.NORMAL;
  }
}
