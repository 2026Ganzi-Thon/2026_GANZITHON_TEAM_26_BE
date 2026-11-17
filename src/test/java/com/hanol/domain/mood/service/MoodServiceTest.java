package com.hanol.domain.mood.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hanol.domain.alert.service.CrisisDetector;
import com.hanol.domain.blanket.entity.BlanketPiece;
import com.hanol.domain.interaction.entity.WarmthMapLog;
import com.hanol.domain.interaction.repository.WarmthMapLogRepository;
import com.hanol.domain.mood.dto.request.MoodRecordCreateRequest;
import com.hanol.domain.mood.dto.response.MoodDetailPatchResponse;
import com.hanol.domain.mood.dto.response.MoodDetailResponse;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.domain.mood.entity.MoodRecord;
import com.hanol.domain.mood.entity.MoodRecordCard;
import com.hanol.domain.mood.entity.ReceiveStatus;
import com.hanol.domain.mood.exception.MoodErrorCode;
import com.hanol.domain.mood.repository.MoodRecordCardRepository;
import com.hanol.domain.mood.repository.MoodRecordRepository;
import com.hanol.domain.mood.repository.StoryRepository;
import com.hanol.domain.user.entity.User;
import com.hanol.domain.user.repository.DailyStatRepository;
import com.hanol.domain.user.repository.UserRepository;
import com.hanol.global.exception.BusinessException;
import com.hanol.global.exception.CommonErrorCode;
import com.hanol.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class MoodServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final Instant NOW_INSTANT = Instant.parse("2026-11-04T00:00:00Z");
  private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZONE);
  private static final Long USER_ID = 1L;
  private static final Long OTHER_USER_ID = 2L;
  private static final Long RECORD_ID = 10L;

  @Mock private CrisisDetector crisisDetector;
  @Mock private UserRepository userRepository;
  @Mock private DailyStatRepository dailyStatRepository;
  @Mock private MoodRecordRepository moodRecordRepository;
  @Mock private MoodRecordCardRepository moodRecordCardRepository;
  @Mock private StoryRepository storyRepository;
  @Mock private WarmthMapLogRepository warmthMapLogRepository;
  @Mock private TransactionTemplate transactionTemplate;

  private MoodService moodService;

  @BeforeEach
  void setUp() {
    moodService =
        new MoodService(
            crisisDetector,
            userRepository,
            dailyStatRepository,
            moodRecordRepository,
            moodRecordCardRepository,
            storyRepository,
            warmthMapLogRepository,
            Clock.fixed(NOW_INSTANT, ZONE),
            transactionTemplate);
  }

  private void stubTransaction() {
    given(transactionTemplate.execute(any()))
        .willAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(null);
            });
  }

  private User stubSavePath() {
    stubTransaction();
    User user = user(USER_ID);
    given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
    given(dailyStatRepository.findByUserIdAndStatDate(USER_ID, NOW.toLocalDate()))
        .willReturn(Optional.empty());
    given(dailyStatRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    given(moodRecordRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    return user;
  }

  private static User user(Long id) {
    User user = User.builder().email("user" + id + "@example.com").password("encoded").build();
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private static MoodRecord record(Long ownerId) {
    MoodRecord record =
        MoodRecord.builder().user(user(ownerId)).recordedAt(NOW).dailySeq(1).build();
    ReflectionTestUtils.setField(record, "id", RECORD_ID);
    return record;
  }

  private static void assertErrorCode(Runnable action, ErrorCode errorCode) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(errorCode);
  }

  @Test
  @DisplayName("사연이 위기면 400_CRISIS_DETECTED, 트랜잭션을 열지 않고 저장하지 않는다")
  void rejectCrisisStory() {
    given(crisisDetector.isCrisis("죽고 싶어")).willReturn(true);

    assertErrorCode(
        () ->
            moodService.createRecord(
                USER_ID, new MoodRecordCreateRequest(List.of(Emotion.TEARFUL), "죽고 싶어")),
        MoodErrorCode.CRISIS_DETECTED);
    verify(transactionTemplate, never()).execute(any());
    verify(moodRecordRepository, never()).save(any());
  }

  @Test
  @DisplayName("위기 검사기가 예외를 던지면 그대로 전파되고 저장하지 않는다")
  void propagateCrisisDetectorFailure() {
    given(crisisDetector.isCrisis(anyString()))
        .willThrow(new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR));

    assertErrorCode(
        () ->
            moodService.createRecord(
                USER_ID, new MoodRecordCreateRequest(List.of(Emotion.TEARFUL), "오늘 힘들었어")),
        CommonErrorCode.INTERNAL_SERVER_ERROR);
    verify(transactionTemplate, never()).execute(any());
    verify(moodRecordRepository, never()).save(any());
  }

  @Test
  @DisplayName("사연이 없으면 위기 검사기를 호출하지 않고 저장한다")
  void skipCrisisCheckWithoutStory() {
    stubSavePath();

    moodService.createRecord(USER_ID, new MoodRecordCreateRequest(List.of(Emotion.LONELY), null));
    moodService.createRecord(USER_ID, new MoodRecordCreateRequest(List.of(Emotion.LONELY), "  "));

    verify(crisisDetector, never()).isCrisis(any());
    verify(storyRepository, never()).save(any());
  }

  @Test
  @DisplayName("같은 감정을 두 번 고르면 400_INVALID_INPUT")
  void rejectDuplicatedEmotion() {
    assertErrorCode(
        () ->
            moodService.createRecord(
                USER_ID,
                new MoodRecordCreateRequest(List.of(Emotion.ANXIETY, Emotion.ANXIETY), null)),
        CommonErrorCode.INVALID_INPUT);
    verify(transactionTemplate, never()).execute(any());
  }

  @Test
  @DisplayName("30분 안에 같은 감정 지도 기록이 있으면 그 감정의 지도 기록은 새로 저장하지 않는다")
  void skipWarmthMapLogWithinThirtyMinutes() {
    stubSavePath();
    LocalDateTime dedupFrom = NOW.minusMinutes(30);
    given(
            warmthMapLogRepository.existsByUserIdAndEmotionAndLoggedAtAfter(
                USER_ID, Emotion.ANXIETY, dedupFrom))
        .willReturn(true);
    given(
            warmthMapLogRepository.existsByUserIdAndEmotionAndLoggedAtAfter(
                USER_ID, Emotion.LONELY, dedupFrom))
        .willReturn(false);

    moodService.createRecord(
        USER_ID, new MoodRecordCreateRequest(List.of(Emotion.ANXIETY, Emotion.LONELY), null));

    ArgumentCaptor<WarmthMapLog> captor = ArgumentCaptor.forClass(WarmthMapLog.class);
    verify(warmthMapLogRepository).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(WarmthMapLog::getEmotion)
        .containsExactly(Emotion.LONELY);
  }

  @Test
  @DisplayName("없는 기록을 상세 조회하면 404_NOT_FOUND")
  void getMissingRecord() {
    given(moodRecordRepository.findById(RECORD_ID)).willReturn(Optional.empty());

    assertErrorCode(() -> moodService.getRecord(USER_ID, RECORD_ID), CommonErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("남의 기록을 상세 조회하면 403_FORBIDDEN")
  void getOthersRecord() {
    given(moodRecordRepository.findById(RECORD_ID)).willReturn(Optional.of(record(OTHER_USER_ID)));

    assertErrorCode(() -> moodService.getRecord(USER_ID, RECORD_ID), CommonErrorCode.FORBIDDEN);
    verify(moodRecordCardRepository, never()).findByMoodRecordIdOrderByCardOrderAsc(anyLong());
  }

  @Test
  @DisplayName("상세 조회에서 RECEIVED 카드의 message 는 연결된 담요 칸 content")
  void getRecordMessageOfReceivedCard() {
    MoodRecord record = record(USER_ID);
    given(moodRecordRepository.findById(RECORD_ID)).willReturn(Optional.of(record));
    MoodRecordCard card =
        MoodRecordCard.builder().moodRecord(record).cardOrder(1).emotion(Emotion.LONELY).build();
    card.receive(
        BlanketPiece.builder().slotIndex(1).content("산책하면 좀 나아졌어요").receivedAt(NOW).build());
    given(moodRecordCardRepository.findByMoodRecordIdOrderByCardOrderAsc(RECORD_ID))
        .willReturn(List.of(card));

    MoodDetailResponse response = moodService.getRecord(USER_ID, RECORD_ID);

    assertThat(response.patches())
        .containsExactly(
            new MoodDetailPatchResponse("LONELY", ReceiveStatus.RECEIVED, "산책하면 좀 나아졌어요"));
  }

  @Test
  @DisplayName("상세 조회에서 DEFAULT_COMFORT 카드의 message 는 null")
  void getRecordMessageOfDefaultComfortCard() {
    MoodRecord record = record(USER_ID);
    given(moodRecordRepository.findById(RECORD_ID)).willReturn(Optional.of(record));
    MoodRecordCard card =
        MoodRecordCard.builder().moodRecord(record).cardOrder(1).emotion(Emotion.TEARFUL).build();
    card.receiveDefaultComfort();
    given(moodRecordCardRepository.findByMoodRecordIdOrderByCardOrderAsc(RECORD_ID))
        .willReturn(List.of(card));

    MoodDetailResponse response = moodService.getRecord(USER_ID, RECORD_ID);

    assertThat(response.patches())
        .containsExactly(
            new MoodDetailPatchResponse("TEARFUL", ReceiveStatus.DEFAULT_COMFORT, null));
  }

  @Test
  @DisplayName("없는 기록을 지우면 404_NOT_FOUND, 삭제를 호출하지 않는다")
  void deleteMissingRecord() {
    given(moodRecordRepository.findById(RECORD_ID)).willReturn(Optional.empty());

    assertErrorCode(() -> moodService.deleteRecord(USER_ID, RECORD_ID), CommonErrorCode.NOT_FOUND);
    verify(moodRecordRepository, never()).delete(any());
  }

  @Test
  @DisplayName("남의 기록을 지우면 403_FORBIDDEN, 삭제를 호출하지 않는다")
  void deleteOthersRecord() {
    given(moodRecordRepository.findById(RECORD_ID)).willReturn(Optional.of(record(OTHER_USER_ID)));

    assertErrorCode(() -> moodService.deleteRecord(USER_ID, RECORD_ID), CommonErrorCode.FORBIDDEN);
    verify(storyRepository, never()).deleteByMoodRecordId(anyLong());
    verify(moodRecordCardRepository, never()).deleteByMoodRecordId(anyLong());
    verify(moodRecordRepository, never()).delete(any());
  }

  @Test
  @DisplayName("그날 기록이 없으면 404_NOT_FOUND")
  void getDayRecordsWhenEmpty() {
    LocalDate date = NOW.toLocalDate();
    given(moodRecordRepository.findByUserIdAndRecordDateOrderByRecordedAtAsc(USER_ID, date))
        .willReturn(List.of());

    assertErrorCode(() -> moodService.getDayRecords(USER_ID, date), CommonErrorCode.NOT_FOUND);
  }
}
