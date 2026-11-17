package com.hanol.domain.cheer.exception;

import com.hanol.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 패치 에러 코드. 검수에서 막힌 사유별로 나뉜다. */
@Getter
@RequiredArgsConstructor
public enum CheerErrorCode implements ErrorCode {
  CHEER_HARSH_LANGUAGE(HttpStatus.BAD_REQUEST, "400_CHEER_HARSH_LANGUAGE", "거친 말이 들어 있어요"),
  CHEER_DEMEANING(HttpStatus.BAD_REQUEST, "400_CHEER_DEMEANING", "누군가를 낮추는 말이 있어요"),
  CHEER_HURTFUL(HttpStatus.BAD_REQUEST, "400_CHEER_HURTFUL", "받는 사람을 다치게 할 수 있어요"),
  CHEER_DISMISSIVE(HttpStatus.BAD_REQUEST, "400_CHEER_DISMISSIVE", "아픈 마음을 작게 만드는 말이 있어요"),
  CHEER_CONTACT_OR_LINK(HttpStatus.BAD_REQUEST, "400_CHEER_CONTACT_OR_LINK", "연락처나 링크는 보낼 수 없어요"),
  CHEER_OFF_TOPIC(HttpStatus.BAD_REQUEST, "400_CHEER_OFF_TOPIC", "패치와 맞지 않는 내용이에요"),
  CHEER_SEND_RESTRICTED(
      HttpStatus.FORBIDDEN, "403_CHEER_SEND_RESTRICTED", "신고가 누적되어 지금은 패치를 보낼 수 없어요"),
  CHEER_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "429_CHEER_COOLDOWN", "패치는 5분에 한 번 보낼 수 있어요"),
  CHEER_ALREADY_REPORTED(HttpStatus.CONFLICT, "409_CHEER_ALREADY_REPORTED", "이미 신고 처리된 메시지예요.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
