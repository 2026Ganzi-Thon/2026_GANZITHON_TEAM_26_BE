package com.hanol.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드 공통 인터페이스. 도메인마다 enum 으로 구현한다. (예: UserErrorCode, CheerErrorCode)
 *
 * <p>코드 형식: {HTTP 상태}_{이름} 예) 400_BAD_REQUEST, 401_TOKEN_EXPIRED, 409_EMAIL_DUPLICATED
 */
public interface ErrorCode {

  HttpStatus getStatus();

  String getCode();

  String getMessage();
}
