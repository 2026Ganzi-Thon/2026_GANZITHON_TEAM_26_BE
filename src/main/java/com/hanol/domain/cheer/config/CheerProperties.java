package com.hanol.domain.cheer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 패치 설정 (app.cheer.*). senderHmacSecret 은 발신자 해시(HMAC) 키이며, 비어 있으면 시작할 때 실패한다. */
@ConfigurationProperties(prefix = "app.cheer")
public record CheerProperties(String senderHmacSecret) {

  public CheerProperties {
    if (senderHmacSecret == null || senderHmacSecret.isBlank()) {
      throw new IllegalStateException("app.cheer.sender-hmac-secret 이 비어 있습니다.");
    }
  }
}
