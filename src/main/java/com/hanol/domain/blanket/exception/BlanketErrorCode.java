package com.hanol.domain.blanket.exception;

import com.hanol.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 담요 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum BlanketErrorCode implements ErrorCode {
  INVALID_SLOT_INDEX(HttpStatus.BAD_REQUEST, "400_INVALID_SLOT_INDEX", "담요 칸 번호가 올바르지 않아요.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
