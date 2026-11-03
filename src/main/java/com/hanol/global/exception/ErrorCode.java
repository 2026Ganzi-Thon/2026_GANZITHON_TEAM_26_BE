package com.hanol.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드 공통 인터페이스.
 * 도메인마다 enum 으로 구현한다. (예: UserErrorCode, CheerErrorCode)
 * 코드 규칙: {기능ID 대분류}{HTTP 상태}_{번호} 예) USR404_1, BLK400_2
 */
public interface ErrorCode {

	HttpStatus getStatus();

	String getCode();

	String getMessage();
}
