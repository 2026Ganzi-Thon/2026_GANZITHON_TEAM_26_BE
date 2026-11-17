package com.hanol.domain.cheer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hanol.domain.blanket.entity.Blanket;
import com.hanol.domain.blanket.entity.BlanketPiece;
import com.hanol.domain.blanket.repository.BlanketPieceRepository;
import com.hanol.domain.cheer.dto.response.CheerReportResponse;
import com.hanol.domain.cheer.dto.response.ReceivedCheerResponse;
import com.hanol.domain.cheer.entity.Cheer;
import com.hanol.domain.cheer.entity.CheerReport;
import com.hanol.domain.cheer.entity.CheerStatus;
import com.hanol.domain.cheer.entity.SendRestriction;
import com.hanol.domain.cheer.repository.CheerReportRepository;
import com.hanol.domain.cheer.repository.CheerRepository;
import com.hanol.domain.cheer.repository.SendRestrictionRepository;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.domain.user.entity.User;
import com.hanol.domain.user.repository.UserRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheerReportServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final Instant NOW_INSTANT = Instant.parse("2026-11-04T00:00:00Z");
  private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZONE);
  private static final LocalDateTime RECEIVED_AT = NOW.minusDays(1);
  private static final Long USER_ID = 1L;
  private static final Long CHEER_ID = 50L;
  private static final Long PIECE_ID = 200L;
  private static final int SLOT_INDEX = 7;
  private static final String MY_HASH = "my-hash";
  private static final String REPORTED_HASH = "reported-sender-hash";
  private static final String GENERATED = "AI 가 만든 기본 메시지예요.";

  @Mock private CheerRepository cheerRepository;
  @Mock private CheerReportRepository cheerReportRepository;
  @Mock private SendRestrictionRepository sendRestrictionRepository;
  @Mock private BlanketPieceRepository blanketPieceRepository;
  @Mock private UserRepository userRepository;
  @Mock private CheerSenderHasher cheerSenderHasher;
  @Mock private DefaultCheerMessageGenerator defaultMessageGenerator;
  @Mock private PlatformTransactionManager transactionManager;

  private CheerReportService service;
  private User user;
  private Cheer reported;
  private BlanketPiece piece;
  private SendRestriction restriction;

  @BeforeEach
  void setUp() {
    service =
        new CheerReportService(
            cheerRepository,
            cheerReportRepository,
            sendRestrictionRepository,
            blanketPieceRepository,
            userRepository,
            cheerSenderHasher,
            defaultMessageGenerator,
            new CheerFallbackMessages(),
            Clock.fixed(NOW_INSTANT, ZONE),
            transactionManager);
    user = User.builder().email("user@example.com").password("encoded").build();
    ReflectionTestUtils.setField(user, "id", USER_ID);

    reported = cheer(CHEER_ID, REPORTED_HASH, Emotion.LONELY, "신고된 패치", CheerStatus.DELIVERED);
    piece =
        BlanketPiece.builder()
            .blanket(Blanket.builder().user(user).build())
            .slotIndex(SLOT_INDEX)
            .cheer(reported)
            .content(reported.getContent())
            .receivedAt(RECEIVED_AT)
            .build();
    ReflectionTestUtils.setField(piece, "id", PIECE_ID);

    restriction = SendRestriction.builder().senderHash(REPORTED_HASH).build();
    given(sendRestrictionRepository.findBySenderHashForUpdate(REPORTED_HASH))
        .willReturn(Optional.of(restriction));
    given(cheerSenderHasher.hash(USER_ID)).willReturn(MY_HASH);
    given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
    given(blanketPieceRepository.findDefaultContents(USER_ID)).willReturn(List.of());
    given(defaultMessageGenerator.generate(any(), anySet())).willReturn(GENERATED);
    given(cheerRepository.findById(CHEER_ID)).willReturn(Optional.of(reported));
    given(blanketPieceRepository.findFirstByCheerIdAndBlanketUserId(CHEER_ID, USER_ID))
        .willReturn(Optional.of(piece));
    given(cheerRepository.changeStatusIf(CHEER_ID, CheerStatus.DELIVERED, CheerStatus.HIDDEN, NOW))
        .willReturn(1);
  }

  private static Cheer cheer(
      Long id, String senderHash, Emotion emotion, String content, CheerStatus status) {
    Cheer cheer =
        Cheer.builder().senderHash(senderHash).targetEmotion(emotion).content(content).build();
    ReflectionTestUtils.setField(cheer, "id", id);
    ReflectionTestUtils.setField(cheer, "status", status);
    return cheer;
  }

  private Cheer givenWaitingCheer(Long id, String content) {
    Cheer waiting = cheer(id, "other-hash", Emotion.LONELY, content, CheerStatus.WAITING);
    given(
            cheerRepository.existsByTargetEmotionAndStatusAndSenderHashNot(
                Emotion.LONELY, CheerStatus.WAITING, MY_HASH))
        .willReturn(true);
    given(
            cheerRepository.findQueueCandidates(
                eq(Emotion.LONELY), eq(CheerStatus.WAITING), eq(MY_HASH), any()))
        .willReturn(List.of(waiting));
    given(cheerRepository.changeStatusIf(id, CheerStatus.WAITING, CheerStatus.DELIVERED, NOW))
        .willReturn(1);
    return waiting;
  }

  private void assertErrorCode(CommonErrorCode errorCode) {
    assertThatThrownBy(() -> service.report(USER_ID, CHEER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(errorCode);
  }

  @Test
  @DisplayName("없는 패치를 신고하면 404")
  void rejectMissingCheer() {
    given(cheerRepository.findById(CHEER_ID)).willReturn(Optional.empty());

    assertErrorCode(CommonErrorCode.NOT_FOUND);
    verify(transactionManager, never()).getTransaction(any());
  }

  @Test
  @DisplayName("이미 숨김 처리된 패치를 신고하면 409")
  void rejectHiddenCheer() {
    ReflectionTestUtils.setField(reported, "status", CheerStatus.HIDDEN);

    assertErrorCode(CommonErrorCode.CONFLICT);
    verify(transactionManager, never()).getTransaction(any());
  }

  @Test
  @DisplayName("내 담요에 없는 패치를 신고하면 403")
  void rejectCheerNotInMyBlanket() {
    given(blanketPieceRepository.findFirstByCheerIdAndBlanketUserId(CHEER_ID, USER_ID))
        .willReturn(Optional.empty());

    assertErrorCode(CommonErrorCode.FORBIDDEN);
    verify(transactionManager, never()).getTransaction(any());
  }

  @Test
  @DisplayName("트랜잭션 안에서 다시 읽었을 때 숨김이면 409, 칸 확인·숨김 처리를 하지 않음")
  void rejectWhenHiddenInsideTransaction() {
    Cheer hidden = cheer(CHEER_ID, REPORTED_HASH, Emotion.LONELY, "신고된 패치", CheerStatus.HIDDEN);
    given(cheerRepository.findById(CHEER_ID))
        .willReturn(Optional.of(reported))
        .willReturn(Optional.of(hidden));

    assertErrorCode(CommonErrorCode.CONFLICT);
    verify(blanketPieceRepository, times(1)).findFirstByCheerIdAndBlanketUserId(CHEER_ID, USER_ID);
    verify(cheerRepository, never()).changeStatusIf(anyLong(), any(), any(), any());
  }

  @Test
  @DisplayName("숨김 처리에서 바뀐 행이 0이면 409, 신고 기록·칸 교체 없음")
  void rejectWhenHideLost() {
    givenWaitingCheer(60L, "새 패치");
    given(cheerRepository.changeStatusIf(CHEER_ID, CheerStatus.DELIVERED, CheerStatus.HIDDEN, NOW))
        .willReturn(0);

    assertErrorCode(CommonErrorCode.CONFLICT);
    verify(cheerReportRepository, never()).save(any());
    verify(cheerRepository, never())
        .changeStatusIf(60L, CheerStatus.WAITING, CheerStatus.DELIVERED, NOW);
    assertThat(piece.getCheer()).isSameAs(reported);
    assertThat(piece.getContent()).isEqualTo("신고된 패치");
  }

  @Test
  @DisplayName("대기열 패치로 교체: 칸 문장이 바뀌고 받은 시각·칸 위치는 그대로, 신고 기록에 발신자 해시 저장")
  void replaceWithQueuedCheer() {
    Cheer waiting = givenWaitingCheer(60L, "새 패치");

    CheerReportResponse response = service.report(USER_ID, CHEER_ID);

    assertThat(response.replaced())
        .isEqualTo(ReceivedCheerResponse.ofCheer(60L, Emotion.LONELY, "새 패치"));
    assertThat(piece.getCheer()).isSameAs(waiting);
    assertThat(piece.getContent()).isEqualTo("새 패치");
    assertThat(piece.getReceivedAt()).isEqualTo(RECEIVED_AT);
    assertThat(piece.getSlotIndex()).isEqualTo(SLOT_INDEX);
    ArgumentCaptor<CheerReport> captor = ArgumentCaptor.forClass(CheerReport.class);
    verify(cheerReportRepository).save(captor.capture());
    assertThat(captor.getValue().getCheer()).isSameAs(reported);
    assertThat(captor.getValue().getSenderHash()).isEqualTo(REPORTED_HASH);
    assertThat(captor.getValue().getReportedAt()).isEqualTo(NOW);
    verify(defaultMessageGenerator, never()).generate(any(), anySet());
  }

  @Test
  @DisplayName("대기열이 비면 기본 메시지로 교체, cheerId null·isDefault true·신고된 패치의 감정")
  void replaceWithDefaultMessageWhenQueueEmpty() {
    given(
            cheerRepository.existsByTargetEmotionAndStatusAndSenderHashNot(
                Emotion.LONELY, CheerStatus.WAITING, MY_HASH))
        .willReturn(false);

    CheerReportResponse response = service.report(USER_ID, CHEER_ID);

    assertThat(response.replaced())
        .isEqualTo(new ReceivedCheerResponse(null, Emotion.LONELY, GENERATED, true));
    assertThat(piece.getCheer()).isNull();
    assertThat(piece.getContent()).isEqualTo(GENERATED);
    verify(cheerRepository, never()).findQueueCandidates(any(), any(), any(), any());
  }

  @Test
  @DisplayName("대기열에서 꺼낼 때 내 sender hash 로 내가 보낸 패치를 제외한다")
  void excludeMyCheersFromQueue() {
    givenWaitingCheer(60L, "새 패치");

    service.report(USER_ID, CHEER_ID);

    verify(cheerRepository)
        .existsByTargetEmotionAndStatusAndSenderHashNot(
            Emotion.LONELY, CheerStatus.WAITING, MY_HASH);
    verify(cheerRepository)
        .findQueueCandidates(eq(Emotion.LONELY), eq(CheerStatus.WAITING), eq(MY_HASH), any());
  }

  @Test
  @DisplayName("발신자의 신고가 3번이 되면 지금부터 24시간 보내기 제한")
  void restrictSenderAtThirdReport() {
    given(
            cheerReportRepository.countBySenderHashAndReportedAtAfter(
                REPORTED_HASH, NOW.minusDays(30)))
        .willReturn(3L);

    service.report(USER_ID, CHEER_ID);

    verify(sendRestrictionRepository).insertIfAbsent(REPORTED_HASH, NOW);
    assertThat(restriction.getRestrictedUntil()).isEqualTo(NOW.plusHours(24));
  }

  @Test
  @DisplayName("발신자의 신고가 2번이면 제한하지 않는다")
  void doNotRestrictBelowThreshold() {
    given(
            cheerReportRepository.countBySenderHashAndReportedAtAfter(
                REPORTED_HASH, NOW.minusDays(30)))
        .willReturn(2L);

    service.report(USER_ID, CHEER_ID);

    assertThat(restriction.getRestrictedUntil()).isNull();
  }

  @Test
  @DisplayName("이미 제한 중이면 신고 수를 세지 않고 해제 시각도 그대로")
  void keepRestrictionWhenAlreadyRestricted() {
    restriction.restrictUntil(NOW.plusHours(3));

    service.report(USER_ID, CHEER_ID);

    verify(cheerReportRepository, never()).countBySenderHashAndReportedAtAfter(any(), any());
    assertThat(restriction.getRestrictedUntil()).isEqualTo(NOW.plusHours(3));
  }

  @Test
  @DisplayName("지난 해제 시각이 30일 안이면 그 시각 이후 신고만 센다")
  void countSinceLastRestrictionEnd() {
    restriction.restrictUntil(NOW.minusDays(5));

    service.report(USER_ID, CHEER_ID);

    verify(cheerReportRepository)
        .countBySenderHashAndReportedAtAfter(REPORTED_HASH, NOW.minusDays(5));
    assertThat(restriction.getRestrictedUntil()).isEqualTo(NOW.minusDays(5));
  }
}
