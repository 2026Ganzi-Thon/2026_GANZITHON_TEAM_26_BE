package com.hanol.domain.auth.service;

import java.util.Locale;

/** 같은 이메일이 대소문자·공백 차이로 다른 키가 되지 않도록 저장·조회 전에 정규화한다. */
final class EmailNormalizer {

  private EmailNormalizer() {}

  static String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
