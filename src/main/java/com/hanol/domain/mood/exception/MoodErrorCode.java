package com.hanol.domain.mood.exception;

import com.hanol.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 마음 기록 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum MoodErrorCode implements ErrorCode {
  CRISIS_DETECTED(HttpStatus.BAD_REQUEST, "400_CRISIS_DETECTED", "위기 표현이 감지되어 기록을 저장하지 않았어요.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
