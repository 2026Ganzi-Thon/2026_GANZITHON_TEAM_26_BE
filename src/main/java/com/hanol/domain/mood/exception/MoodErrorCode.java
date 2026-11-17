package com.hanol.domain.mood.exception;

import com.hanol.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 마음 기록 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum MoodErrorCode implements ErrorCode {
  CRISIS_DETECTED(HttpStatus.BAD_REQUEST, "400_CRISIS_DETECTED", "위기 표현이 감지되어 기록을 저장하지 않았어요."),
  DUPLICATE_EMOTION_SELECTED(
      HttpStatus.BAD_REQUEST, "400_DUPLICATE_EMOTION_SELECTED", "같은 감정을 두 번 고를 수 없어요."),
  NO_MOOD_RECORD_TODAY(
      HttpStatus.BAD_REQUEST, "400_NO_MOOD_RECORD_TODAY", "오늘 기록이 없어 패치를 받을 수 없어요."),
  NO_RECENT_MOOD_RECORD(HttpStatus.BAD_REQUEST, "400_NO_RECENT_MOOD_RECORD", "최근 24시간 이내 기록이 없어요.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
