package com.hanol.domain.interaction.exception;

import com.hanol.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 함께 쉬는 방(ITR-REST) 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum InteractionErrorCode implements ErrorCode {
  CHEER_NOT_RECEIVED_YET(
      HttpStatus.BAD_REQUEST, "400_CHEER_NOT_RECEIVED_YET", "패치를 아직 받지 않았어요. 먼저 패치를 받아 주세요."),
  REST_ROOM_NOT_ENTERED(
      HttpStatus.BAD_REQUEST, "400_REST_ROOM_NOT_ENTERED", "입장 기록이 없어요. 다시 입장해 주세요.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
