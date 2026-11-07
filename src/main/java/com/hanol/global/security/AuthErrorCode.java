package com.hanol.global.security;

import com.hanol.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "401_UNAUTHORIZED", "유효하지 않은 토큰입니다."),
  EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "401_TOKEN_EXPIRED", "만료된 토큰입니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
