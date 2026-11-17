package com.hanol.domain.cheer.service;

import com.hanol.domain.mood.entity.Emotion;
import java.util.Set;

/** 대기열이 비었거나 4번째 이상 기록일 때 주는 기본 메시지. 트랜잭션 밖에서 호출하고, 실패하지 않는다. */
public interface DefaultCheerMessageGenerator {

  /** 이 사용자가 이미 받은 기본 메시지(receivedMessages, 공백 trim)와 같은 문장은 주지 않는다. */
  String generate(Emotion emotion, Set<String> receivedMessages);
}
