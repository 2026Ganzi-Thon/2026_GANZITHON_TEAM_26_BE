package com.hanol.domain.interaction.service;

import com.hanol.domain.interaction.dto.response.RestRoomEntryResponse;
import com.hanol.domain.interaction.dto.response.RestRoomSignalResponse;
import com.hanol.domain.interaction.exception.InteractionErrorCode;
import com.hanol.domain.interaction.repository.RestRoomRepository;
import com.hanol.domain.mood.entity.ReceiveStatus;
import com.hanol.domain.mood.repository.MoodRecordCardRepository;
import com.hanol.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestRoomService {

  private final RestRoomRepository restRoomRepository;
  private final MoodRecordCardRepository moodRecordCardRepository;
  private final Clock clock;

  /** ITR-REST-001 함께 쉬는 방 입장. 10분 안에 재입장하면 기존 입장 시각을 이어 쓴다. */
  public RestRoomEntryResponse enter(Long userId) {
    LocalDate today = LocalDate.now(clock);
    boolean receivedToday =
        moodRecordCardRepository.existsByMoodRecordUserIdAndMoodRecordRecordDateAndReceiveStatusNot(
            userId, today, ReceiveStatus.NOT_RECEIVED);
    if (!receivedToday) {
      throw new BusinessException(InteractionErrorCode.CHEER_NOT_RECEIVED_YET);
    }

    Instant now = Instant.now(clock);
    Instant enteredAt = restRoomRepository.findEnteredAt(userId).orElse(now);
    restRoomRepository.saveEnteredAt(userId, enteredAt);
    restRoomRepository.signal(userId, now);
    long currentCount = restRoomRepository.countActive(now);

    log.info("Rest room entered. userId={}", userId);
    return new RestRoomEntryResponse((int) currentCount, toLocalDateTime(enteredAt));
  }

  /** ITR-REST-001 함께 쉬는 방 신호(하트비트). 30초마다 호출된다. 퇴장했거나 신호가 끊겨 정리된 상태면 다시 받아주지 않는다. */
  public RestRoomSignalResponse signal(Long userId) {
    restRoomRepository
        .findEnteredAt(userId)
        .orElseThrow(() -> new BusinessException(InteractionErrorCode.REST_ROOM_NOT_ENTERED));
    if (!restRoomRepository.isMember(userId)) {
      throw new BusinessException(InteractionErrorCode.REST_ROOM_NOT_ENTERED);
    }

    Instant now = Instant.now(clock);
    restRoomRepository.refreshEnteredAtTtl(userId);
    restRoomRepository.signal(userId, now);
    long currentCount = restRoomRepository.countActive(now);
    return new RestRoomSignalResponse((int) currentCount);
  }

  /** ITR-REST-001 함께 쉬는 방 퇴장. 인원에서 즉시 제외한다. */
  public void exit(Long userId) {
    restRoomRepository.remove(userId);
    log.info("Rest room exited. userId={}", userId);
  }

  private LocalDateTime toLocalDateTime(Instant instant) {
    return LocalDateTime.ofInstant(instant, clock.getZone());
  }
}
