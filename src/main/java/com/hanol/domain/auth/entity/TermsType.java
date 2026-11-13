package com.hanol.domain.auth.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 회원가입 시 동의해야 하는 필수 약관. 약관 내용이 바뀌면 version 을 올린다. */
@Getter
@RequiredArgsConstructor
public enum TermsType {
  TERMS_OF_SERVICE("v1.0"),
  PRIVACY_POLICY("v1.0"),
  USAGE_PRINCIPLES("v1.0");

  private final String version;
}
