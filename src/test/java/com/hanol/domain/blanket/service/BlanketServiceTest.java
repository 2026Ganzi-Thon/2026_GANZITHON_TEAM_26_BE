package com.hanol.domain.blanket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.hanol.domain.blanket.dto.response.BlanketSlotResponse;
import com.hanol.domain.blanket.dto.response.BlanketSlotSummaryResponse;
import com.hanol.domain.blanket.dto.response.BlanketViewResponse;
import com.hanol.domain.blanket.entity.BlanketPiece;
import com.hanol.domain.blanket.repository.BlanketPieceRepository;
import com.hanol.domain.blanket.repository.BlanketPieceRepository.SlotEmotion;
import com.hanol.domain.blanket.repository.BlanketRepository;
import com.hanol.domain.cheer.entity.Cheer;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.domain.mood.repository.MoodRecordRepository;
import com.hanol.global.exception.BusinessException;
import com.hanol.global.exception.CommonErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BlanketServiceTest {

  private static final Long USER_ID = 1L;
  private static final List<Long> THREE_BLANKETS = List.of(11L, 25L, 40L);
  private static final LocalDateTime RECEIVED_AT = LocalDateTime.of(2026, 11, 4, 23, 30);
  private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 11, 3, 10, 0);
  private static final String MESSAGE = "오늘도 수고했어요";

  @Mock private BlanketRepository blanketRepository;
  @Mock private BlanketPieceRepository blanketPieceRepository;
  @Mock private MoodRecordRepository moodRecordRepository;

  private BlanketService service;

  @BeforeEach
  void setUp() {
    service = new BlanketService(blanketRepository, blanketPieceRepository, moodRecordRepository);
  }

  private void assertErrorCode(Runnable call, CommonErrorCode errorCode) {
    assertThatThrownBy(call::run)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(errorCode);
  }

  private BlanketPiece piece(int slotIndex, Cheer cheer, LocalDateTime receivedAt) {
    Emotion emotion = cheer == null ? Emotion.ANXIETY : cheer.getTargetEmotion();
    return BlanketPiece.builder()
        .slotIndex(slotIndex)
        .cheer(cheer)
        .emotion(emotion)
        .content(MESSAGE)
        .receivedAt(receivedAt)
        .build();
  }

  private record TestSlotEmotion(int slotIndex, Emotion emotion) implements SlotEmotion {

    @Override
    public int getSlotIndex() {
      return slotIndex;
    }

    @Override
    public Emotion getEmotion() {
      return emotion;
    }
  }

  private Cheer cheer() {
    Cheer cheer =
        Cheer.builder()
            .senderHash("hash")
            .targetEmotion(Emotion.values()[0])
            .content(MESSAGE)
            .build();
    ReflectionTestUtils.setField(cheer, "id", 77L);
    ReflectionTestUtils.setField(cheer, "createdAt", SENT_AT);
    return cheer;
  }

  @Test
  @DisplayName("담요가 없으면 번호 없이나 1번 조회 시 빈 30칸 1번 담요")
  void emptyBlanketWhenNoBlanket() {
    given(blanketRepository.findIdsByUserIdOrderByIdAsc(USER_ID)).willReturn(List.of());

    for (Long number : new Long[] {null, 1L}) {
      BlanketViewResponse response = service.getBlanket(USER_ID, number);
      assertThat(response.blanketId()).isEqualTo(1L);
      assertThat(response.slots()).hasSize(30).noneMatch(BlanketSlotSummaryResponse::filled);
      assertThat(response.slots().get(0).slotIndex()).isEqualTo(1);
      assertThat(response.slots().get(29).slotIndex()).isEqualTo(30);
      assertThat(response.hasPrevious()).isFalse();
      assertThat(response.hasNext()).isFalse();
    }
  }

  @Test
  @DisplayName("담요가 없을 때 2번 조회는 NOT_FOUND")
  void notFoundSecondBlanketWhenNoBlanket() {
    given(blanketRepository.findIdsByUserIdOrderByIdAsc(USER_ID)).willReturn(List.of());

    assertErrorCode(() -> service.getBlanket(USER_ID, 2L), CommonErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("번호 없이 조회하면 가장 최근 담요, 채워진 칸이 표시된다")
  void currentBlanketIsLatest() {
    given(blanketRepository.findIdsByUserIdOrderByIdAsc(USER_ID)).willReturn(THREE_BLANKETS);
    given(blanketPieceRepository.findSlotEmotionsByBlanketId(40L))
        .willReturn(
            List.of(
                new TestSlotEmotion(1, Emotion.ANXIETY),
                new TestSlotEmotion(2, Emotion.LONELY),
                new TestSlotEmotion(5, Emotion.ANXIETY)));

    BlanketViewResponse response = service.getBlanket(USER_ID, null);

    assertThat(response.blanketId()).isEqualTo(3L);
    assertThat(response.slots()).hasSize(30);
    assertThat(response.slots())
        .filteredOn(BlanketSlotSummaryResponse::filled)
        .extracting(BlanketSlotSummaryResponse::slotIndex)
        .containsExactly(1, 2, 5);
    assertThat(response.slots())
        .filteredOn(BlanketSlotSummaryResponse::filled)
        .extracting(BlanketSlotSummaryResponse::emotion)
        .containsExactly(Emotion.ANXIETY, Emotion.LONELY, Emotion.ANXIETY);
    assertThat(response.slots())
        .filteredOn(slot -> !slot.filled())
        .extracting(BlanketSlotSummaryResponse::emotion)
        .containsOnlyNulls();
    assertThat(response.hasPrevious()).isTrue();
    assertThat(response.hasNext()).isFalse();
  }

  @Test
  @DisplayName("담요 3개일 때 1·2·3번의 이전/다음 여부")
  void previousAndNextFlags() {
    given(blanketRepository.findIdsByUserIdOrderByIdAsc(USER_ID)).willReturn(THREE_BLANKETS);
    given(blanketPieceRepository.findSlotEmotionsByBlanketId(anyLong())).willReturn(List.of());

    BlanketViewResponse first = service.getBlanket(USER_ID, 1L);
    BlanketViewResponse second = service.getBlanket(USER_ID, 2L);
    BlanketViewResponse third = service.getBlanket(USER_ID, 3L);

    assertThat(first.hasPrevious()).isFalse();
    assertThat(first.hasNext()).isTrue();
    assertThat(second.hasPrevious()).isTrue();
    assertThat(second.hasNext()).isTrue();
    assertThat(third.hasPrevious()).isTrue();
    assertThat(third.hasNext()).isFalse();
  }

  @Test
  @DisplayName("없는 담요 번호(0, 개수+1)는 NOT_FOUND")
  void notFoundOutOfRangeBlanket() {
    given(blanketRepository.findIdsByUserIdOrderByIdAsc(USER_ID)).willReturn(THREE_BLANKETS);

    assertErrorCode(() -> service.getBlanket(USER_ID, 0L), CommonErrorCode.NOT_FOUND);
    assertErrorCode(() -> service.getBlanket(USER_ID, 4L), CommonErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("칸 번호가 1~30 밖이면 BAD_REQUEST")
  void badRequestOutOfRangeSlot() {
    assertErrorCode(() -> service.getSlot(USER_ID, 1L, 0), CommonErrorCode.BAD_REQUEST);
    assertErrorCode(() -> service.getSlot(USER_ID, 1L, 31), CommonErrorCode.BAD_REQUEST);
  }

  @Test
  @DisplayName("없는 담요 번호의 칸 조회는 NOT_FOUND")
  void notFoundSlotOfMissingBlanket() {
    given(blanketRepository.findIdsByUserIdOrderByIdAsc(USER_ID)).willReturn(THREE_BLANKETS);

    assertErrorCode(() -> service.getSlot(USER_ID, 4L, 1), CommonErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("빈 칸 조회는 NOT_FOUND")
  void notFoundEmptySlot() {
    given(blanketRepository.findIdsByUserIdOrderByIdAsc(USER_ID)).willReturn(THREE_BLANKETS);
    given(blanketPieceRepository.findWithCheerByBlanketIdAndSlotIndex(25L, 7))
        .willReturn(Optional.empty());

    assertErrorCode(() -> service.getSlot(USER_ID, 2L, 7), CommonErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("사람이 보낸 패치 칸은 cheerId·emotion·sentAt 이 채워지고 isDefault=false")
  void slotOfSentCheer() {
    Cheer cheer = cheer();
    given(blanketRepository.findIdsByUserIdOrderByIdAsc(USER_ID)).willReturn(THREE_BLANKETS);
    given(blanketPieceRepository.findWithCheerByBlanketIdAndSlotIndex(11L, 3))
        .willReturn(Optional.of(piece(3, cheer, RECEIVED_AT)));

    BlanketSlotResponse response = service.getSlot(USER_ID, 1L, 3);

    assertThat(response.message()).isEqualTo(MESSAGE);
    assertThat(response.receivedAt()).isEqualTo(RECEIVED_AT);
    assertThat(response.slotIndex()).isEqualTo(3);
    assertThat(response.cheerId()).isEqualTo(77L);
    assertThat(response.isDefault()).isFalse();
    assertThat(response.emotion()).isEqualTo(cheer.getTargetEmotion());
    assertThat(response.sentAt()).isEqualTo(SENT_AT);
  }

  @Test
  @DisplayName("기본 메시지 칸은 cheerId·sentAt 이 null 이고 isDefault=true, emotion은 그대로 내려준다")
  void slotOfDefaultMessage() {
    given(blanketRepository.findIdsByUserIdOrderByIdAsc(USER_ID)).willReturn(THREE_BLANKETS);
    given(blanketPieceRepository.findWithCheerByBlanketIdAndSlotIndex(40L, 1))
        .willReturn(Optional.of(piece(1, null, RECEIVED_AT)));

    BlanketSlotResponse response = service.getSlot(USER_ID, 3L, 1);

    assertThat(response.cheerId()).isNull();
    assertThat(response.emotion()).isEqualTo(Emotion.ANXIETY);
    assertThat(response.sentAt()).isNull();
    assertThat(response.isDefault()).isTrue();
  }

  @Test
  @DisplayName("받은 날짜에 마음 기록이 있으면 hasMoodRecordOnDate=true, 없으면 false")
  void hasMoodRecordOnReceivedDate() {
    LocalDateTime nextDay = RECEIVED_AT.plusDays(1);
    given(blanketRepository.findIdsByUserIdOrderByIdAsc(USER_ID)).willReturn(THREE_BLANKETS);
    given(blanketPieceRepository.findWithCheerByBlanketIdAndSlotIndex(11L, 1))
        .willReturn(Optional.of(piece(1, null, RECEIVED_AT)));
    given(blanketPieceRepository.findWithCheerByBlanketIdAndSlotIndex(11L, 2))
        .willReturn(Optional.of(piece(2, null, nextDay)));
    given(moodRecordRepository.existsByUserIdAndRecordDate(USER_ID, RECEIVED_AT.toLocalDate()))
        .willReturn(true);
    given(moodRecordRepository.existsByUserIdAndRecordDate(USER_ID, nextDay.toLocalDate()))
        .willReturn(false);

    assertThat(service.getSlot(USER_ID, 1L, 1).hasMoodRecordOnDate()).isTrue();
    assertThat(service.getSlot(USER_ID, 1L, 2).hasMoodRecordOnDate()).isFalse();
  }
}
