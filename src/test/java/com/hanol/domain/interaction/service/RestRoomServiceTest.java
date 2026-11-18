package com.hanol.domain.interaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hanol.domain.interaction.exception.InteractionErrorCode;
import com.hanol.domain.interaction.repository.RestRoomRepository;
import com.hanol.domain.mood.entity.ReceiveStatus;
import com.hanol.domain.mood.repository.MoodRecordCardRepository;
import com.hanol.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestRoomServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final Instant NOW_INSTANT = Instant.parse("2026-11-16T12:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);
  private static final Long USER_ID = 1L;

  @Mock private RestRoomRepository restRoomRepository;
  @Mock private MoodRecordCardRepository moodRecordCardRepository;

  private RestRoomService restRoomService;

  @BeforeEach
  void setUp() {
    restRoomService =
        new RestRoomService(
            restRoomRepository, moodRecordCardRepository, Clock.fixed(NOW_INSTANT, ZONE));
  }

  @Test
  @DisplayName("입장하면 저장소가 집계한 현재 접속 인원 수를 그대로 응답에 담는다")
  void entersAndReturnsCurrentConnectedCount() {
    given(
            moodRecordCardRepository
                .existsByMoodRecordUserIdAndMoodRecordRecordDateAndReceiveStatusNot(
                    USER_ID, TODAY, ReceiveStatus.NOT_RECEIVED))
        .willReturn(true);
    given(restRoomRepository.findEnteredAt(USER_ID)).willReturn(Optional.empty());
    given(restRoomRepository.countActive(NOW_INSTANT)).willReturn(5L);

    var response = restRoomService.enter(USER_ID);

    assertThat(response.currentCount()).isEqualTo(5);
  }

  @Test
  @DisplayName("입장 중이면 하트비트가 통과해 인원 집계를 갱신한다")
  void signalRefreshesWhileEntered() {
    given(restRoomRepository.findEnteredAt(USER_ID)).willReturn(Optional.of(NOW_INSTANT));
    given(restRoomRepository.isMember(USER_ID)).willReturn(true);
    given(restRoomRepository.countActive(NOW_INSTANT)).willReturn(3L);

    var response = restRoomService.signal(USER_ID);

    assertThat(response.currentCount()).isEqualTo(3);
    verify(restRoomRepository).signal(USER_ID, NOW_INSTANT);
  }

  @Test
  @DisplayName("퇴장 직후 하트비트가 와도 인원 집계에 다시 넣지 않는다")
  void signalAfterExitDoesNotRejoin() {
    given(restRoomRepository.findEnteredAt(USER_ID)).willReturn(Optional.of(NOW_INSTANT));
    given(restRoomRepository.isMember(USER_ID)).willReturn(false);

    assertThatThrownBy(() -> restRoomService.signal(USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(InteractionErrorCode.REST_ROOM_NOT_ENTERED);
    verify(restRoomRepository, never()).signal(USER_ID, NOW_INSTANT);
  }
}
