package com.hanol.domain.alert.service;

import org.springframework.stereotype.Component;

// AI 연결 전 임시 구현
@Component
public class StubCrisisDetector implements CrisisDetector {

  @Override
  public boolean isCrisis(String content) {
    return false;
  }
}
