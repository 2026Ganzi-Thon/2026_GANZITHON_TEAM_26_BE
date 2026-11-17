package com.hanol.domain.cheer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hanol.domain.cheer.dto.request.CheerSendRequest;
import com.hanol.domain.cheer.dto.response.CheerAvailabilityResponse;
import com.hanol.domain.cheer.dto.response.CheerSendResponse;
import com.hanol.domain.cheer.dto.response.EmotionQuestionGroup;
import com.hanol.domain.cheer.entity.Cheer;
import com.hanol.domain.cheer.entity.CheerStatus;
import com.hanol.domain.cheer.entity.SendRestriction;
import com.hanol.domain.cheer.exception.CheerErrorCode;
import com.hanol.domain.cheer.repository.CheerRepository;
import com.hanol.domain.cheer.repository.CheerRepository.EmotionCount;
import com.hanol.domain.cheer.repository.SendRestrictionRepository;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.domain.user.entity.DailyStat;
import com.hanol.domain.user.entity.User;
import com.hanol.domain.user.repository.DailyStatRepository;
import com.hanol.domain.user.repository.UserRepository;
import com.hanol.global.exception.BusinessException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class CheerServiceTest {

  private static final Long USER_ID = 1L;
  private static final String SENDER_HASH = "a".repeat(64);
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneId.of("Asia/Seoul"));
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 9, 0);
  private static final CheerSendRequest REQUEST =
      new CheerSendRequest(Emotion.LONELY, "산책하면 좀 나아졌어요");

  @Mock private CheerRepository cheerRepository;
  @Mock private CheerReviewer cheerReviewer;
  @Mock private CheerSenderHasher cheerSenderHasher;
  @Mock private SendRestrictionRepository sendRestrictionRepository;
  @Mock private UserRepository userRepository;
  @Mock private DailyStatRepository dailyStatRepository;
  @Mock private TransactionTemplate transactionTemplate;

  private CheerService cheerService;

  @BeforeEach
  void setUp() {
    cheerService =
        new CheerService(
            cheerRepository,
            cheerReviewer,
            cheerSenderHasher,
            sendRestrictionRepository,
            userRepository,
            dailyStatRepository,
            CLOCK,
            transactionTemplate);
  }

  @Test
  @DisplayName("sendable 은 true 이고 감정 11개가 모두 있다")
  void availabilityContainsAllEmotions() {
    given(cheerRepository.countGroupByTargetEmotion(CheerStatus.WAITING)).willReturn(List.of());

    CheerAvailabilityResponse response = cheerService.getAvailability(USER_ID);

    assertThat(response.sendable()).isTrue();
    assertThat(response.questions())
        .hasSize(11)
        .extracting(EmotionQuestionGroup::emotion)
        .containsExactlyInAnyOrder(Emotion.values());
  }

  @Test
  @DisplayName("대기 수가 적은 감정부터, 같으면 Emotion 선언 순서로 정렬된다")
  void sortByWaitingCountThenDeclarationOrder() {
    given(cheerRepository.countGroupByTargetEmotion(CheerStatus.WAITING))
        .willReturn(List.of(count(Emotion.LONELY, 2), count(Emotion.ANXIETY, 1)));

    CheerAvailabilityResponse response = cheerService.getAvailability(USER_ID);

    assertThat(response.questions())
        .extracting(EmotionQuestionGroup::emotion)
        .containsExactly(
            Emotion.UNMOTIVATED,
            Emotion.HOPELESS_FUTURE,
            Emotion.EXHAUSTED,
            Emotion.INADEQUATE,
            Emotion.NOWHERE_TO_VENT,
            Emotion.TEARFUL,
            Emotion.RESTLESS,
            Emotion.OVERWHELMED,
            Emotion.UNSURE,
            Emotion.ANXIETY,
            Emotion.LONELY);
  }

  @Test
  @DisplayName("각 감정의 questions 는 그 감정의 cheerQuestion 1개")
  void questionsAreCheerQuestionOfEachEmotion() {
    given(cheerRepository.countGroupByTargetEmotion(CheerStatus.WAITING)).willReturn(List.of());

    CheerAvailabilityResponse response = cheerService.getAvailability(USER_ID);

    assertThat(response.questions())
        .allSatisfy(
            group ->
                assertThat(group.questions()).containsExactly(group.emotion().getCheerQuestion()));
  }

  @Test
  @DisplayName("대기 수는 WAITING 상태로만 센다")
  void countsOnlyWaitingCheers() {
    given(cheerRepository.countGroupByTargetEmotion(CheerStatus.WAITING)).willReturn(List.of());

    cheerService.getAvailability(USER_ID);

    verify(cheerRepository).countGroupByTargetEmotion(CheerStatus.WAITING);
  }

  @Test
  @DisplayName("보내기 제한 중이면 sendable false, 해제 시각, 빈 질문 목록이고 대기 수를 세지 않는다")
  void availabilityWhenRestricted() {
    givenRestriction(NOW.plusHours(3));

    CheerAvailabilityResponse response = cheerService.getAvailability(USER_ID);

    assertThat(response.sendable()).isFalse();
    assertThat(response.restrictedUntil()).isEqualTo(NOW.plusHours(3));
    assertThat(response.questions()).isEmpty();
    verify(cheerRepository, never()).countGroupByTargetEmotion(any());
  }

  @Test
  @DisplayName("해제 시각이 지났으면 sendable true, restrictedUntil null, 질문 목록이 있다")
  void availabilityAfterRestrictionExpired() {
    givenRestriction(NOW.minusMinutes(1));
    given(cheerRepository.countGroupByTargetEmotion(CheerStatus.WAITING)).willReturn(List.of());

    CheerAvailabilityResponse response = cheerService.getAvailability(USER_ID);

    assertThat(response.sendable()).isTrue();
    assertThat(response.restrictedUntil()).isNull();
    assertThat(response.questions()).hasSize(11);
  }

  @Test
  @DisplayName("보내기 제한 중에 보내면 403_CHEER_SEND_RESTRICTED 이고 검수하지 않는다")
  void rejectSendWhenRestricted() {
    givenRestriction(NOW.plusHours(3));

    assertThatThrownBy(() -> cheerService.send(USER_ID, REQUEST))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(CheerErrorCode.CHEER_SEND_RESTRICTED);
    verify(cheerReviewer, never()).review(any());
  }

  @Test
  @DisplayName("정상이면 WAITING 패치를 발신자 해시와 저장하고 sent_count 와 last_sent_at 을 반영한다")
  void sendNormalCheer() {
    User user = User.builder().email("user@example.com").password("encoded").build();
    DailyStat dailyStat = DailyStat.builder().user(user).statDate(TODAY).build();
    dailyStat.increaseSentCount(NOW.minusHours(1));
    givenNormalSave(user);
    given(dailyStatRepository.findByUserIdAndStatDate(USER_ID, TODAY))
        .willReturn(Optional.of(dailyStat));

    CheerSendResponse response = cheerService.send(USER_ID, REQUEST);

    assertThat(response.crisisDetected()).isFalse();
    ArgumentCaptor<Cheer> captor = ArgumentCaptor.forClass(Cheer.class);
    verify(cheerRepository).save(captor.capture());
    Cheer cheer = captor.getValue();
    assertThat(cheer.getStatus()).isEqualTo(CheerStatus.WAITING);
    assertThat(cheer.getSenderHash()).isEqualTo(SENDER_HASH);
    assertThat(cheer.getTargetEmotion()).isEqualTo(Emotion.LONELY);
    assertThat(cheer.getContent()).isEqualTo("산책하면 좀 나아졌어요");
    assertThat(dailyStat.getSentCount()).isEqualTo(2);
    assertThat(dailyStat.getLastSentAt()).isEqualTo(NOW);
    verify(userRepository).findByIdForUpdate(USER_ID);
    verify(dailyStatRepository, never()).save(any());
  }

  @Test
  @DisplayName("오늘 daily_stats 가 없으면 새로 만들고 sent_count 는 1")
  void createDailyStatWhenMissing() {
    User user = User.builder().email("user@example.com").password("encoded").build();
    givenNormalSave(user);
    given(dailyStatRepository.findByUserIdAndStatDate(USER_ID, TODAY)).willReturn(Optional.empty());
    given(dailyStatRepository.save(any(DailyStat.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    cheerService.send(USER_ID, REQUEST);

    ArgumentCaptor<DailyStat> captor = ArgumentCaptor.forClass(DailyStat.class);
    verify(dailyStatRepository).save(captor.capture());
    DailyStat created = captor.getValue();
    assertThat(created.getStatDate()).isEqualTo(TODAY);
    assertThat(created.getUser()).isSameAs(user);
    assertThat(created.getSentCount()).isEqualTo(1);
    assertThat(created.getLastSentAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("위기면 crisisDetected true 이고 저장·집계하지 않는다")
  void crisisIsNotSaved() {
    given(cheerReviewer.review(REQUEST.content())).willReturn(CheerReviewResult.CRISIS);

    CheerSendResponse response = cheerService.send(USER_ID, REQUEST);

    assertThat(response.crisisDetected()).isTrue();
    verifyNoInteractions(transactionTemplate, userRepository, dailyStatRepository, cheerRepository);
  }

  @ParameterizedTest
  @EnumSource(
      value = CheerReviewResult.class,
      names = {"NORMAL", "CRISIS"},
      mode = EnumSource.Mode.EXCLUDE)
  @DisplayName("부적절이면 사유별 에러 코드를 던지고 저장·집계하지 않는다")
  void rejectedIsNotSaved(CheerReviewResult result) {
    given(cheerReviewer.review(REQUEST.content())).willReturn(result);

    assertThatThrownBy(() -> cheerService.send(USER_ID, REQUEST))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(CheerErrorCode.valueOf("CHEER_" + result.name()));
    verifyNoInteractions(transactionTemplate, userRepository, dailyStatRepository, cheerRepository);
  }

  private void givenRestriction(LocalDateTime restrictedUntil) {
    SendRestriction restriction = SendRestriction.builder().senderHash(SENDER_HASH).build();
    restriction.restrictUntil(restrictedUntil);
    given(cheerSenderHasher.hash(USER_ID)).willReturn(SENDER_HASH);
    given(sendRestrictionRepository.findBySenderHash(SENDER_HASH))
        .willReturn(Optional.of(restriction));
  }

  private void givenNormalSave(User user) {
    given(cheerReviewer.review(REQUEST.content())).willReturn(CheerReviewResult.NORMAL);
    given(transactionTemplate.execute(any()))
        .willAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(null);
            });
    given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
    given(cheerSenderHasher.hash(USER_ID)).willReturn(SENDER_HASH);
    given(cheerRepository.save(any(Cheer.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
  }

  private static EmotionCount count(Emotion emotion, long count) {
    return new EmotionCount() {
      @Override
      public Emotion getEmotion() {
        return emotion;
      }

      @Override
      public long getCount() {
        return count;
      }
    };
  }
}
