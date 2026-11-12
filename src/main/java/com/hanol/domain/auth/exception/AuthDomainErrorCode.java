package com.hanol.domain.auth.exception;

import com.hanol.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 회원가입·이메일 인증·로그인 에러 코드. 토큰 관련 에러는 global/security/AuthErrorCode 를 쓴다. */
@Getter
@RequiredArgsConstructor
public enum AuthDomainErrorCode implements ErrorCode {
  EMAIL_DUPLICATED(HttpStatus.CONFLICT, "409_EMAIL_DUPLICATED", "이미 가입된 이메일입니다."),
  EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "400_EMAIL_NOT_VERIFIED", "이메일 인증을 먼저 완료해 주세요."),
  TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "400_TERMS_NOT_AGREED", "필수 약관에 모두 동의해 주세요."),
  EMAIL_REQUEST_LIMITED(
      HttpStatus.TOO_MANY_REQUESTS,
      "429_EMAIL_REQUEST_LIMITED",
      "인증 요청이 너무 많아요. 5분 뒤에 다시 시도해 주세요."),
  EMAIL_CODE_NOT_FOUND(
      HttpStatus.NOT_FOUND, "404_EMAIL_CODE_NOT_FOUND", "인증번호가 만료되었거나 요청 이력이 없습니다. 다시 요청해 주세요."),
  EMAIL_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "400_EMAIL_CODE_MISMATCH", "인증번호가 일치하지 않습니다."),
  LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "401_LOGIN_FAILED", "이메일 또는 비밀번호가 올바르지 않습니다."),
  ACCOUNT_LOCKED(
      HttpStatus.LOCKED, "423_ACCOUNT_LOCKED", "로그인에 5번 실패해 5분 동안 로그인할 수 없어요. 잠시 후 다시 시도해 주세요.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
