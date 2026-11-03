package com.hanol.global.exception;

import lombok.Getter;

/**
 * 비즈니스 로직에서 의도적으로 던지는 예외.
 * 예) throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
 * GlobalExceptionHandler 가 받아서 공통 응답 형식으로 변환한다.
 */
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}
}
