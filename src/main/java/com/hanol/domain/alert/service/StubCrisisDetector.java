package com.hanol.domain.alert.service;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StubCrisisDetector implements CrisisDetector {

  private final CrisisKeywordMatcher keywordMatcher;

  @Override
  public boolean isCrisis(String content) {
    return keywordMatcher.matches(content);
  }
}
