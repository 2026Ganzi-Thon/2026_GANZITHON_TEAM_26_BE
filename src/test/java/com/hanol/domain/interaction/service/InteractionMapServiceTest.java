package com.hanol.domain.interaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.hanol.domain.interaction.dto.response.InteractionMapStatus;
import com.hanol.domain.interaction.repository.WarmthMapLogRepository;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.domain.mood.entity.MoodRecord;
import com.hanol.domain.mood.entity.MoodRecordCard;
import com.hanol.domain.mood.repository.MoodRecordCardRepository;
import com.hanol.domain.mood.repository.MoodRecordRepository;
import com.hanol.domain.user.entity.DailyStat;
import com.hanol.domain.user.entity.User;
import com.hanol.domain.user.repository.DailyStatRepository;
import com.hanol.global.exception.BusinessException;
import com.hanol.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
class InteractionMapServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final Instant NOW_INSTANT = Instant.parse("2026-11-16T12:00:00Z");
  private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZONE);
  private static final Long USER_ID = 1L;
  private static final Long RECORD_ID = 10L;

  @Mock private DailyStatRepository dailyStatRepository;
  @Mock private MoodRecordRepository moodRecordRepository;
  @Mock private MoodRecordCardRepository moodRecordCardRepository;
  @Mock private WarmthMapLogRepository warmthMapLogRepository;

  private InteractionMapService interactionMapService;

  @BeforeEach
  void setUp() {
    interactionMapService =
        new InteractionMapService(
            dailyStatRepository,
            moodRecordRepository,
            moodRecordCardRepository,
            warmthMapLogRepository,
            Clock.fixed(NOW_INSTANT, ZONE));
  }

  private static DailyStat dailyStat(int pickCount) {
    DailyStat dailyStat = DailyStat.builder().user(user()).statDate(NOW.toLocalDate()).build();
    for (int i = 0; i < pickCount; i++) {
      dailyStat.increasePickCount();
    }
    return dailyStat;
  }

  private static User user() {
    User user = User.builder().email("user1@example.com").password("encoded").build();
    ReflectionTestUtils.setField(user, "id", USER_ID);
    return user;
  }

  private static MoodRecord record(long id) {
    MoodRecord record = MoodRecord.builder().user(user()).recordedAt(NOW).dailySeq(1).build();
    ReflectionTestUtils.setField(record, "id", id);
    return record;
  }

  private static MoodRecordCard card(MoodRecord record, Emotion emotion, int order) {
    return MoodRecordCard.builder().moodRecord(record).cardOrder(order).emotion(emotion).build();
  }

  private void stubRecentRecords(
      int pickCount, List<MoodRecord> records, List<MoodRecordCard> cards) {
    given(dailyStatRepository.findByUserIdAndStatDate(USER_ID, NOW.toLocalDate()))
        .willReturn(Optional.of(dailyStat(pickCount)));
    given(
            moodRecordRepository.findByUserIdAndRecordedAtBetweenOrderByRecordedAtAsc(
                USER_ID, NOW.minusHours(24), NOW))
        .willReturn(records);
    given(
            moodRecordCardRepository.findByMoodRecordIdInOrderByCardOrderAsc(
                records.stream().map(MoodRecord::getId).toList()))
        .willReturn(cards);
  }

  private void stubRecentRecord(int pickCount, Emotion emotion) {
    MoodRecord record = record(RECORD_ID);
    stubRecentRecords(pickCount, List.of(record), List.of(card(record, emotion, 1)));
  }

  @Test
  @DisplayName("최근 24시간 이내 기록이 없으면 400_BAD_REQUEST")
  void noRecentRecord() {
    given(
            moodRecordRepository.findByUserIdAndRecordedAtBetweenOrderByRecordedAtAsc(
                USER_ID, NOW.minusHours(24), NOW))
        .willReturn(List.of());

    assertThatThrownBy(() -> interactionMapService.getMap(USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(CommonErrorCode.BAD_REQUEST);
  }

  @Test
  @DisplayName("24시간 이내 같은 감정이 5명 이상이면 ACTIVE, 정확한 수를 내려준다")
  void activeWithExactCount() {
    stubRecentRecord(1, Emotion.ANXIETY);
    given(
            warmthMapLogRepository.countOtherUsers(
                eq(Emotion.ANXIETY), eq(NOW.minusHours(24)), eq(NOW), eq(USER_ID)))
        .willReturn(7L);

    var response = interactionMapService.getMap(USER_ID);

    assertThat(response.status()).isEqualTo(InteractionMapStatus.ACTIVE);
    assertThat(response.emotions()).hasSize(1);
    assertThat(response.emotions().get(0).count()).isEqualTo(7);
    assertThat(response.blanketNotStackedNotice()).isFalse();
  }

  @Test
  @DisplayName("1~4명이어도 정확한 수를 그대로 내려준다")
  void activeReturnsExactCountEvenWhenFew() {
    stubRecentRecord(1, Emotion.ANXIETY);
    given(warmthMapLogRepository.countOtherUsers(any(), any(), any(), eq(USER_ID))).willReturn(3L);

    var response = interactionMapService.getMap(USER_ID);

    assertThat(response.status()).isEqualTo(InteractionMapStatus.ACTIVE);
    assertThat(response.emotions().get(0).count()).isEqualTo(3);
  }

  @Test
  @DisplayName("24시간 이내는 없고 7일 이내만 있으면 QUIET")
  void fallsBackToQuiet() {
    stubRecentRecord(1, Emotion.LONELY);
    given(
            warmthMapLogRepository.countOtherUsers(
                eq(Emotion.LONELY), eq(NOW.minusHours(24)), eq(NOW), eq(USER_ID)))
        .willReturn(0L);
    given(
            warmthMapLogRepository.countOtherUsers(
                eq(Emotion.LONELY), eq(NOW.minusDays(7)), eq(NOW.minusHours(24)), eq(USER_ID)))
        .willReturn(6L);

    var response = interactionMapService.getMap(USER_ID);

    assertThat(response.status()).isEqualTo(InteractionMapStatus.QUIET);
    assertThat(response.emotions().get(0).count()).isEqualTo(6);
  }

  @Test
  @DisplayName("7일 이내도 없으면 FIRST_LIGHT, emotions는 빈 배열")
  void firstLightWhenAlone() {
    stubRecentRecord(1, Emotion.LONELY);
    given(warmthMapLogRepository.countOtherUsers(any(), any(), any(), eq(USER_ID))).willReturn(0L);

    var response = interactionMapService.getMap(USER_ID);

    assertThat(response.status()).isEqualTo(InteractionMapStatus.FIRST_LIGHT);
    assertThat(response.emotions()).isEmpty();
  }

  @Test
  @DisplayName("오늘 4번째 이상 기록이면 담요 미적립 안내가 true")
  void notifiesBlanketNotStacked() {
    stubRecentRecord(4, Emotion.LONELY);
    given(warmthMapLogRepository.countOtherUsers(any(), any(), any(), eq(USER_ID))).willReturn(0L);

    var response = interactionMapService.getMap(USER_ID);

    assertThat(response.blanketNotStackedNotice()).isTrue();
  }

  @Test
  @DisplayName("24시간 이내 여러 기록에서 고른 감정은 하나도 빠짐없이, 각 감정별로 겹쳐서(overlay) 집계된다")
  void aggregatesEmotionsAcrossMultipleRecordsWithoutMerging() {
    MoodRecord earlierRecord = record(RECORD_ID);
    MoodRecord latestRecord = record(RECORD_ID + 1);
    stubRecentRecords(
        2,
        List.of(earlierRecord, latestRecord),
        List.of(card(earlierRecord, Emotion.ANXIETY, 1), card(latestRecord, Emotion.LONELY, 1)));
    given(
            warmthMapLogRepository.countOtherUsers(
                eq(Emotion.ANXIETY), eq(NOW.minusHours(24)), eq(NOW), eq(USER_ID)))
        .willReturn(5L);
    given(
            warmthMapLogRepository.countOtherUsers(
                eq(Emotion.LONELY), eq(NOW.minusHours(24)), eq(NOW), eq(USER_ID)))
        .willReturn(3L);

    var response = interactionMapService.getMap(USER_ID);

    assertThat(response.status()).isEqualTo(InteractionMapStatus.ACTIVE);
    assertThat(response.emotions()).hasSize(2);
    assertThat(response.emotions()).extracting("count").containsExactlyInAnyOrder(5, 3);
  }

  @Test
  @DisplayName("같은 감정으로 여러 번 기록해도 해당 감정은 한 번만 집계한다")
  void dedupesSameEmotionAcrossMultipleRecords() {
    MoodRecord earlierRecord = record(RECORD_ID);
    MoodRecord latestRecord = record(RECORD_ID + 1);
    stubRecentRecords(
        2,
        List.of(earlierRecord, latestRecord),
        List.of(card(earlierRecord, Emotion.ANXIETY, 1), card(latestRecord, Emotion.ANXIETY, 1)));
    given(warmthMapLogRepository.countOtherUsers(any(), any(), any(), eq(USER_ID))).willReturn(4L);

    var response = interactionMapService.getMap(USER_ID);

    assertThat(response.emotions()).hasSize(1);
  }
}
