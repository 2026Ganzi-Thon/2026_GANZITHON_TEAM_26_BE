package com.hanol.domain.cheer.service;

import com.hanol.domain.cheer.exception.CheerErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 패치 검수 결과. errorCode 가 있으면 부적절(발송 거절)이다. */
@Getter
@RequiredArgsConstructor
public enum CheerReviewResult {
  NORMAL(null),
  CRISIS(null),
  HARSH_LANGUAGE(CheerErrorCode.CHEER_HARSH_LANGUAGE),
  DEMEANING(CheerErrorCode.CHEER_DEMEANING),
  HURTFUL(CheerErrorCode.CHEER_HURTFUL),
  DISMISSIVE(CheerErrorCode.CHEER_DISMISSIVE),
  CONTACT_OR_LINK(CheerErrorCode.CHEER_CONTACT_OR_LINK),
  OFF_TOPIC(CheerErrorCode.CHEER_OFF_TOPIC);

  private final CheerErrorCode errorCode;

  public boolean isRejected() {
    return errorCode != null;
  }
}
