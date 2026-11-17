package com.hanol.domain.cheer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
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
import com.hanol.domain.cheer.exception.CheerErrorCode;
import com.hanol.domain.cheer.repository.CheerReportRepository;
import com.hanol.domain.cheer.repository.CheerRepository;
import com.hanol.domain.cheer.repository.SendRestrictionRepository;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.domain.user.entity.User;
import com.hanol.domain.user.repository.UserRepository;
import com.hanol.global.exception.BusinessException;
import com.hanol.global.exception.CommonErrorCode;
import com.hanol.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
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
  private static final String REPORTED_HASH = "reported-sender-hash";
  private static final String GENERATED = "AI 가 만든 기본 메시지예요.";

  @Mock private CheerRepository cheerRepository;
  @Mock private CheerReportRepository cheerReportRepository;
  @Mock private SendRestrictionRepository sendRestrictionRepository;
  @Mock private BlanketPieceRepository blanketPieceRepository;
  @Mock private UserRepository userRepository;
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
            defaultMessageGenerator,
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
    given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
    given(blanketPieceRepository.findReceivedDefaultMessages(USER_ID)).willReturn(Set.of());
    given(blanketPieceRepository.existsByCheerIdAndBlanketUserId(CHEER_ID, USER_ID))
        .willReturn(true);
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

  private void assertErrorCode(ErrorCode errorCode) {
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

    assertErrorCode(CheerErrorCode.CHEER_ALREADY_REPORTED);
    verify(transactionManager, never()).getTransaction(any());
  }

  @Test
  @DisplayName("내 담요에 없는 패치를 신고하면 403")
  void rejectCheerNotInMyBlanket() {
    given(blanketPieceRepository.existsByCheerIdAndBlanketUserId(CHEER_ID, USER_ID))
        .willReturn(false);

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

    assertErrorCode(CheerErrorCode.CHEER_ALREADY_REPORTED);
    verify(blanketPieceRepository, never()).findFirstByCheerIdAndBlanketUserId(CHEER_ID, USER_ID);
    verify(blanketPieceRepository, times(1)).existsByCheerIdAndBlanketUserId(CHEER_ID, USER_ID);
    verify(cheerRepository, never()).changeStatusIf(anyLong(), any(), any(), any());
  }

  @Test
  @DisplayName("숨김 처리에서 바뀐 행이 0이면 409, 신고 기록·칸 교체 없음")
  void rejectWhenHideLost() {
    given(cheerRepository.changeStatusIf(CHEER_ID, CheerStatus.DELIVERED, CheerStatus.HIDDEN, NOW))
        .willReturn(0);

    assertErrorCode(CheerErrorCode.CHEER_ALREADY_REPORTED);
    verify(cheerReportRepository, never()).save(any());
    assertThat(piece.getCheer()).isSameAs(reported);
    assertThat(piece.getContent()).isEqualTo("신고된 패치");
  }

  @Test
  @DisplayName(
      "신고하면 대기열을 보지 않고 AI 기본 메시지로 교체: cheerId null·isDefault true·신고된 패치 감정,"
          + " 칸 문장만 바뀌고 받은 시각·칸 위치는 그대로, 신고 기록에 발신자 해시 저장")
  void replaceWithDefaultMessage() {
    CheerReportResponse response = service.report(USER_ID, CHEER_ID);

    assertThat(response.replaced())
        .isEqualTo(ReceivedCheerResponse.ofDefault(Emotion.LONELY, GENERATED, SLOT_INDEX, false));
    assertThat(piece.getCheer()).isNull();
    assertThat(piece.getContent()).isEqualTo(GENERATED);
    assertThat(piece.getReceivedAt()).isEqualTo(RECEIVED_AT);
    assertThat(piece.getSlotIndex()).isEqualTo(SLOT_INDEX);
    ArgumentCaptor<CheerReport> captor = ArgumentCaptor.forClass(CheerReport.class);
    verify(cheerReportRepository).save(captor.capture());
    assertThat(captor.getValue().getCheer()).isSameAs(reported);
    assertThat(captor.getValue().getSenderHash()).isEqualTo(REPORTED_HASH);
    assertThat(captor.getValue().getReportedAt()).isEqualTo(NOW);
    verify(cheerRepository, never()).findQueueCandidates(any(), any(), any(), any());
    verify(cheerRepository, never())
        .existsByTargetEmotionAndStatusAndSenderHashNot(any(), any(), any());
  }

  @Test
  @DisplayName("발신자의 신고가 3번이 되면 지금부터 24시간 보내기 제한")
  void restrictSenderAtThirdReport() {
    given(
            cheerReportRepository.countBySenderHashAndReportedAtAfter(
                REPORTED_HASH, NOW.minusDays(30)))
        .willReturn(3L);

    service.report(USER_ID, CHEER_ID);

    verify(sendRestrictionRepository).insertOrLock(REPORTED_HASH, NOW);
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
