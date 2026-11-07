package com.hanol.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "400_BAD_REQUEST", "입력값이 올바르지 않습니다."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "401_UNAUTHORIZED", "로그인이 필요합니다."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "403_FORBIDDEN", "접근 권한이 없습니다."),
  NOT_FOUND(HttpStatus.NOT_FOUND, "404_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
  METHOD_NOT_ALLOWED(
      HttpStatus.METHOD_NOT_ALLOWED, "405_METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."),
  INTERNAL_SERVER_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR, "500_INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
